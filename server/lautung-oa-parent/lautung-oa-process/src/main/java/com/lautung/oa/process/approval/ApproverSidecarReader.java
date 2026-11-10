package com.lautung.oa.process.approval;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.process.publication.ApproverSidecar;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 读部署里冻结的 {@code oa-approvers.json} sidecar（ADR 0069）：同一份已发布模板在
 * 任何时候的审批人解析都可复现——绝不读「当前模板」的规则行。
 *
 * <p>没有 sidecar 的旧部署返回空列表（监听器 no-op，BPMN assignee 照旧）；sidecar
 * 结构坏掉按解析失败显式报错，不静默跳过。
 */
@Component
public class ApproverSidecarReader {

    private final RepositoryService repositoryService;

    public ApproverSidecarReader(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public List<ApproverSidecar.SidecarRule> frozenRules(String processDefinitionId) {
        if (!StringUtils.hasText(processDefinitionId)) {
            return List.of();
        }
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        if (definition == null) {
            return List.of();
        }
        try (InputStream resource = repositoryService.getResourceAsStream(
                definition.getDeploymentId(), ApproverSidecar.SIDECAR_ENTRY)) {
            if (resource == null) {
                return List.of();
            }
            return ApproverSidecar.parse(resource);
        } catch (IOException e) {
            throw new BusinessException(201, "oa-approvers.json 无法读取");
        } catch (org.flowable.common.engine.api.FlowableObjectNotFoundException missingSidecar) {
            // 没有 sidecar 的旧已发布部署：监听器 no-op，BPMN assignee 照旧（ADR 0069）。
            return List.of();
        }
    }
}
