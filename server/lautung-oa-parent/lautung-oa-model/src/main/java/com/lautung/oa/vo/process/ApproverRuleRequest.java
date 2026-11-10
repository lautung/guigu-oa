package com.lautung.oa.vo.process;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 单个审批节点的审批人规则请求（ADR 0069，#285）：每节点单选一种来源。
 * 部门负责人按发起人所在部门解析，不带 targetId；角色/岗位必须给 targetId。
 */
@Data
@Schema(description = "审批人规则")
public class ApproverRuleRequest {

    @NotBlank(message = "审批节点不能为空")
    @Schema(description = "BPMN userTask id")
    private String taskDefinitionKey;

    @NotBlank(message = "审批人来源类型不能为空")
    @Schema(description = "来源类型：DEPT_LEADER / ROLE / POST")
    private String sourceType;

    @Schema(description = "目标id（角色/岗位）")
    private Long targetId;
}
