package com.atguigu.vo.process;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "流程表单")
public class ProcessFormVo {

	@Schema(description = "审批模板id")
	private Long processTemplateId;

	@Schema(description = "审批类型id")
	private Long processTypeId;

	@Schema(description = "表单值")
	private String formValues;

	@Schema(description = "附件草稿标识")
	private String attachmentDraftId;

	@Schema(description = "本次发起绑定的附件 id")
	private List<Long> attachmentIds;

}
