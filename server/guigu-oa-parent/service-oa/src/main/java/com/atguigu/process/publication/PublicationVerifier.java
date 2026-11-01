package com.atguigu.process.publication;

import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessTemplatePublication;
import com.atguigu.process.ProcessDefinitionStorage;
import com.atguigu.process.deployment.ProcessDeploymentEngine;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 发布判定 module（#175/#189，ADR 0003）：只回答一个问题——
 * 「这份审批模板的发布证据与引擎绑定当前是否可用、什么理由」。
 * 发布、恢复、可发起共用同一判定；恢复不得猜测原版本，证据不足时拒绝发起与恢复。
 *
 * <p>判定结果 {@link Verdict} 分两部分：证据问题理由（沿用既有对外原文，null 表示无问题）
 * 与绑定三态（定义缺失 / 绑定不一致+内部理由 / 可用）。两类问题由读侧消费方归并成
 * 同一条对外文案，本 module 不抛业务异常、不暴露 Flowable 类型：引擎交互只走
 * {@link ProcessDeploymentEngine} port（部署动作的 Flowable 触点在 adapter，
 * 审批运行时触点仍是 {@code com.atguigu.process.ProcessEngineApproval}）。
 */
@Component
public class PublicationVerifier {

    /**
     * 绑定不一致的内部理由标记：只作 verdict 内部区分，不对外出现——
     * 「已绑定的流程定义当前不可用」降为内部理由标记（#189）。
     */
    private static final String UNUSABLE_BINDING = "已绑定的流程定义当前不可用";

    private final ProcessDefinitionStorage processDefinitionStorage;
    private final ProcessDeploymentEngine deploymentEngine;

    public PublicationVerifier(
            ProcessDefinitionStorage processDefinitionStorage,
            ProcessDeploymentEngine deploymentEngine) {
        this.processDefinitionStorage = processDefinitionStorage;
        this.deploymentEngine = deploymentEngine;
    }

    /**
     * 判定这份模板的发布证据与引擎绑定。证据有问题时不查引擎（binding 为 null）；
     * 绑定可用时携带引擎侧定义快照。
     */
    public Verdict verify(ProcessTemplate template, ProcessTemplatePublication publication) {
        String evidenceProblem = evidenceProblem(template, publication);
        if (evidenceProblem != null) {
            return new Verdict(evidenceProblem, null);
        }
        return new Verdict(null, binding(publication));
    }

    /**
     * 发布证据问题理由，原文沿用既有判定：null 表示证据可靠。
     */
    private String evidenceProblem(ProcessTemplate template, ProcessTemplatePublication publication) {
        if (publication == null) return "缺少原发布内容的可靠绑定";
        if (!StringUtils.hasText(publication.getArtifactPath())) return "缺少发布产物路径";
        if (!StringUtils.hasText(publication.getArtifactSha256())) return "缺少发布产物校验值";
        if (!processDefinitionStorage.isContentAddressedPath(
                publication.getArtifactPath(), publication.getArtifactSha256())) return "发布产物路径与校验值不一致";
        if (!StringUtils.hasText(publication.getProcessDefinitionKey())
                || !publication.getProcessDefinitionKey().equals(template.getProcessDefinitionKey())) return "流程定义 key 与模板记录不一致";
        try {
            if (!processDefinitionStorage.matchesSha256(publication.getArtifactPath(), publication.getArtifactSha256())) {
                return "发布流程 zip 缺失或内容已变化";
            }
        } catch (IOException | IllegalArgumentException e) {
            return "发布流程 zip 无法读取";
        }
        return null;
    }

    /**
     * 绑定三态：按记录的 definitionId 查引擎定义；记录的部署 id 缺失不算不一致
     * （切库恢复按可靠依据回填，ADR 0003），与引擎不一致或已挂起才算。
     */
    private Binding binding(ProcessTemplatePublication publication) {
        if (!StringUtils.hasText(publication.getProcessDefinitionId())) {
            return new Binding.DefinitionMissing();
        }
        ProcessDeploymentEngine.DefinitionSnapshot definition =
                deploymentEngine.definitionById(publication.getProcessDefinitionId());
        if (definition == null) {
            return new Binding.DefinitionMissing();
        }
        if (!publication.getProcessDefinitionKey().equals(definition.key())
                || (StringUtils.hasText(publication.getDeploymentId())
                && !publication.getDeploymentId().equals(definition.deploymentId()))
                || definition.suspended()) {
            return new Binding.Inconsistent(UNUSABLE_BINDING);
        }
        return new Binding.Usable(new Definition(
                definition.id(), definition.key(), definition.deploymentId(), definition.suspended()));
    }

    /**
     * 判定结果：证据问题理由 + 绑定三态。证据有问题时 binding 为 null。
     */
    public record Verdict(String evidenceProblem, Binding binding) {

        /** 证据可靠且绑定可用；发起解析与可用性标注以此为准。 */
        public boolean usable() {
            return evidenceProblem == null && binding instanceof Binding.Usable;
        }

        /** 绑定可用时的引擎侧定义；不可用时为 null。 */
        public Definition definition() {
            return binding instanceof Binding.Usable usable ? usable.definition() : null;
        }
    }

    /** 绑定三态：定义缺失 / 绑定不一致+理由 / 可用。 */
    public sealed interface Binding {

        /** 记录的 definitionId 缺失，或引擎里查不到这份定义。 */
        record DefinitionMissing() implements Binding {
        }

        /**
         * 引擎定义与发布绑定不一致（key 不一致、部署 id 不一致或已挂起）；
         * reason 是内部理由标记，不对外出现。
         */
        record Inconsistent(String reason) implements Binding {
        }

        /** 绑定可用，携带引擎侧定义。 */
        record Usable(Definition definition) implements Binding {
        }
    }

    /** 绑定可用时的流程定义快照：id / key / deploymentId / suspended。 */
    public record Definition(String id, String key, String deploymentId, boolean suspended) {
    }
}
