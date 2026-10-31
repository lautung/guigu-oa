package com.atguigu.vo.process;

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

	@Schema(description = "当前审批人")
	private String currentAuditor;

	@Schema(description = "状态（0：默认 1：审批中 2：审批通过 -1：拒绝）")
	private Integer status;

	private String taskId;

}