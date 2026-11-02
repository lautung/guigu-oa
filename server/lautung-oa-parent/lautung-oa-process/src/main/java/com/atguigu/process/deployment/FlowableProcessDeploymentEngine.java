package com.atguigu.process.deployment;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.process.ProcessDefinitionStorage;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * ProcessDeploymentEngine 的 Flowable 适配器：持有 RepositoryService 与产物存储，
 * 是部署动作唯一的 Flowable 触点（审批运行时在 ProcessEngineApproval）。
 * {@link #deployVerified} 整体自 OaProcessTemplateServiceImpl 原样随迁（#188），
 * 部署期摘要前后校验、内容寻址复查、定义计数、挂起检查与文案不做改动。
 */
@Component
public class FlowableProcessDeploymentEngine implements ProcessDeploymentEngine {

    private final RepositoryService repositoryService;
    private final ProcessDefinitionStorage processDefinitionStorage;

    public FlowableProcessDeploymentEngine(
            RepositoryService repositoryService,
            ProcessDefinitionStorage processDefinitionStorage) {
        this.repositoryService = repositoryService;
        this.processDefinitionStorage = processDefinitionStorage;
    }

    @Override
    public DefinitionSnapshot definitionById(String definitionId) {
        if (!StringUtils.hasText(definitionId)) return null;
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId)
                .singleResult();
        if (definition == null) return null;
        return new DefinitionSnapshot(
                definition.getId(),
                definition.getKey(),
                definition.getDeploymentId(),
                definition.isSuspended());
    }

    @Override
    public DeploymentResult deployVerified(String path, String key, String expectedSha256) {
        String sha256;
        try {
            sha256 = processDefinitionStorage.sha256(path);
        } catch (IOException e) {
            throw new GuiguException(201, "流程 zip 不存在或无法读取");
        }
        if (!processDefinitionStorage.isContentAddressedPath(path, sha256)
                || (expectedSha256 != null && !expectedSha256.equals(sha256))) {
            throw new GuiguException(201, "发布流程 zip 内容或路径摘要不匹配");
        }
        Deployment deployment;
        try (var input = processDefinitionStorage.open(path)) {
            if (input == null) throw new GuiguException(201, "流程 zip 不存在");
            try (var zip = new ZipInputStream(new BufferedInputStream(input))) {
                deployment = repositoryService.createDeployment()
                        .addZipInputStream(zip)
                        .name(path)
                        .deploy();
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取或部署流程 zip 失败", e);
        }
        try {
            if (!processDefinitionStorage.matchesSha256(path, sha256)) {
                throw new GuiguException(201, "发布流程 zip 内容已变化");
            }
        } catch (IOException e) {
            throw new GuiguException(201, "发布流程 zip 无法读取");
        }
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .processDefinitionKey(key)
                .list();
        if (definitions.size() != 1) {
            throw new GuiguException(201, "流程 zip 无法让指定审批模板发起");
        }
        ProcessDefinition definition = definitions.get(0);
        if (definition.isSuspended()) throw new GuiguException(201, "流程定义当前不可发起");
        return new DeploymentResult(deployment.getId(), definition.getId(), sha256);
    }
}
