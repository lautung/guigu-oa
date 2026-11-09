package com.lautung.oa.vo.process;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "Process")
public class ProcessVo {

	private Long id;

	private Date createTime;

	@Schema(description = "更新时间（公共字段口径，ADR 0031；#181 起进全部行形状）")
	private Date updateTime;

	@Schema(description = "审批code")
	private String processCode;

	@Schema(description = "用户id")
	private Long userId;
	private String name;

	@TableField("process_template_id")
	private Long processTemplateId;
	private String processTemplateName;

	@Schema(description = "审批类型id")
	private Long processTypeId;
	private String processTypeName;

	@Schema(description = "标题")
	private String title;

	@Schema(description = "描述")
	private String description;

	@Schema(description = "表单属性值")
	private String formValues;

	@Schema(description = "流程实例id")
	private String processInstanceId;

	@Schema(description = "状态（0：默认 1：审批中 2：审批通过 -1：拒绝 -2：已撤回）")
	private Integer status;

	private String taskId;

	@Schema(description = "待处理行动作面（#288：APPROVE 通过/拒绝、CANDIDATE 候选人之一处理即认领、ADD_SIGN 加签处理；仅待处理行下发）")
	private String actionKind;

	@Schema(description = "前加签挂起标记（#288）：true 表示本行是挂起中的原处理人，等待加签人处理、不能动作")
	private Boolean suspended;

	@Schema(description = "催办标记（#288）：最近 24 小时内该审批存在 URGE 账本行（process 级判定）")
	private Boolean urged;

	@Schema(description = "业务类型（#289，ADR 0067）：值域 <域>.<单据>，供前端标来源；通用审批为空")
	private String businessType;

	@Schema(description = "业务单据 id（#289，ADR 0067）：通用审批为空；单据表不存 process_id，反向回查靠本列")
	private Long businessId;

}