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

	@Schema(description = "发起时抄送的用户 id")
	private List<Long> ccUserIds;

	@Schema(description = "发起提交标识，同一次提交重复送达时相同；缺省表示不去重")
	private String idempotencyKey;

}
