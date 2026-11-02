package com.lautung.oa.demo;

import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.process.ProcessDefinitionStorage;
import com.lautung.oa.process.service.ApprovalTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 演示审批模板的发布只在演示初始化时发生：迁移种下的是未发布模板，
 * 这里补上流程产物并走正常发布流程，让发布记录与引擎部署一起产生。
 * 模板已发布（无论来自上一次启动还是人工发布）时只读检查，不重复发布。
 */
@Component
@Profile("demo")
public class DemoApprovalPublisher {

    static final String PROCESS_DEFINITION_KEY = "demoApproval";
    private static final String FIND_TEMPLATE_IDS =
            "SELECT id FROM oa_process_template WHERE process_definition_key = ? AND is_deleted = 0 ORDER BY id";
    private static final Logger log = LoggerFactory.getLogger(DemoApprovalPublisher.class);

    private final ApprovalTemplate templateService;
    private final ProcessDefinitionStorage storage;
    private final JdbcTemplate jdbcTemplate;

    public DemoApprovalPublisher(
            ApprovalTemplate templateService,
            ProcessDefinitionStorage storage,
            JdbcTemplate jdbcTemplate) {
        this.templateService = templateService;
        this.storage = storage;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Returns the published and startable demo template, or null when the demo cannot be seeded. */
    ProcessTemplate ensurePublished() {
        List<Long> templateIds = jdbcTemplate.query(
                FIND_TEMPLATE_IDS, (result, row) -> result.getLong("id"), PROCESS_DEFINITION_KEY);
        if (templateIds.size() != 1) {
            log.warn("Compose 演示审批模板不唯一或不存在，跳过演示审批写入");
            return null;
        }
        Long templateId = templateIds.get(0);
        ProcessTemplate template = templateService.read(templateId);
        if (template == null) {
            log.warn("Compose 演示审批模板不存在，跳过演示审批写入");
            return null;
        }
        if (templateService.canWrite(templateId)) {
            publish(template);
        }

        ProcessTemplate published = templateService.findUniquePublishedByProcessDefinitionKey(PROCESS_DEFINITION_KEY);
        if (published == null || !Boolean.TRUE.equals(published.getAvailableForInitiation())) {
            log.warn("Compose 演示审批模板不可发起，保留只读状态并跳过演示审批写入: {}；"
                            + "若演示数据卷早于本次修复，执行 docker compose down -v 后重启",
                    published == null ? "已发布模板不唯一或不存在" : published.getAvailabilityMessage());
            return null;
        }
        return published;
    }

    private void publish(ProcessTemplate template) {
        ProcessDefinitionStorage.UploadedProcessDefinition artifact;
        try {
            artifact = storage.store(DemoApprovalArtifact.ARCHIVE_NAME, DemoApprovalArtifact.zip());
        } catch (IOException e) {
            throw new IllegalStateException("写入演示流程 zip 失败", e);
        }
        template.setProcessDefinitionKey(artifact.processDefinitionKey());
        template.setProcessDefinitionPath(artifact.processDefinitionPath());
        templateService.saveUnpublished(template);
        templateService.publish(template.getId());
        log.info("Compose 演示审批模板已发布 path={} sha256={}",
                artifact.processDefinitionPath(), artifact.sha256());
    }
}
