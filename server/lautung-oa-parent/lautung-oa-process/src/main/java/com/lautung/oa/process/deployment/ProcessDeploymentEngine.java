package com.lautung.oa.process.deployment;

/**
 * 模板发布 / 切库恢复对流程引擎的部署 port。本 interface 不暴露 Flowable 类型：
 * 部署与流程定义查询收在唯一实现里，实现是部署动作的 Flowable 触点
 * （审批运行时的 Flowable 调用点仍是 {@code com.lautung.oa.process.ProcessEngineApproval}）。
 */
public interface ProcessDeploymentEngine {

    /**
     * 按流程定义 id 查当前定义；id 为空或定义不存在返回 null。
     */
    DefinitionSnapshot definitionById(String definitionId);

    /**
     * 按路径与 key 部署流程 zip：部署期做产物摘要前后校验、内容寻址复查、定义计数与
     * 挂起检查，校验失败抛业务异常（码与文案由实现拥有）。
     *
     * @param expectedSha256 发布绑定期望的产物摘要，null 表示不与部署时摘要比对
     */
    DeploymentResult deployVerified(String path, String key, String expectedSha256);

    /** 部署成功的结果：部署 id、命中的流程定义 id 与部署时产物摘要。 */
    record DeploymentResult(String deploymentId, String definitionId, String sha256) {
    }

    /** 流程定义的引擎侧快照，供发布绑定一致性判定使用。 */
    record DefinitionSnapshot(String id, String key, String deploymentId, boolean suspended) {
    }
}
