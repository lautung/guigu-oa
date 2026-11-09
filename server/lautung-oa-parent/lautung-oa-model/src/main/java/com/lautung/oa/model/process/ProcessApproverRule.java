package com.lautung.oa.model.process;

import com.lautung.oa.model.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批人规则（ADR 0065/0069，#285）：模板子表按 模板 + BPMN userTask id 存每节点的
 * 单选审批人来源。未发布可改；发布时随产物冻结成 {@code oa-approvers.json}，
 * 此后运行期不读这张表决定已发布模板的审批人。
 *
 * <p>来源类型见 {@code com.lautung.oa.process.approval.ApproverSourceType}；
 * {@code target_id} 只对角色/岗位有意义（部门负责人按发起人所在部门解析，不指定目标）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "审批人规则")
@TableName("oa_process_approver_rule")
public class ProcessApproverRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "审批模板id")
    @TableField("process_template_id")
    private Long processTemplateId;

    @Schema(description = "BPMN userTask id")
    @TableField("task_definition_key")
    private String taskDefinitionKey;

    @Schema(description = "来源类型：DEPT_LEADER / ROLE / POST")
    @TableField("source_type")
    private String sourceType;

    @Schema(description = "目标id（角色/岗位）")
    @TableField("target_id")
    private Long targetId;
}
