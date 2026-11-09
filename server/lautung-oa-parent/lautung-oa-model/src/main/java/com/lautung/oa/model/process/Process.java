package com.lautung.oa.model.process;

import com.lautung.oa.model.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Process")
@TableName("oa_process")
public class Process extends BaseEntity {

	private static final long serialVersionUID = 1L;

	@Schema(description = "审批code")
	@TableField("process_code")
	private String processCode;

	@Schema(description = "用户id")
	@TableField("user_id")
	private Long userId;

	@Schema(description = "审批模板id")
	@TableField("process_template_id")
	private Long processTemplateId;

	@Schema(description = "审批类型id")
	@TableField("process_type_id")
	private Long processTypeId;

	@Schema(description = "标题")
	@TableField("title")
	private String title;

	@Schema(description = "描述")
	@TableField("description")
	private String description;

	@Schema(description = "表单值")
	@TableField("form_values")
	private String formValues;

	@Schema(description = "流程实例id")
	@TableField("process_instance_id")
	private String processInstanceId;

	@Schema(description = "状态（0：默认 1：审批中 2：审批通过 -1：拒绝 -2：已撤回）")
	@TableField("status")
	private Integer status;

	@Schema(description = "发起提交标识（同一次提交重复送达时相同）")
	@TableField("idempotency_key")
	private String idempotencyKey;

	@Schema(description = "业务类型（值域 <域>.<单据>，如 hr.transfer；通用审批为空）")
	@TableField("business_type")
	private String businessType;

	@Schema(description = "业务单据 id（单据表不存 process_id，详情按两列回查最近一条审批；通用审批为空）")
	@TableField("business_id")
	private Long businessId;
}