package com.atguigu.vo.process;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Process")
public class ProcessQueryVo {

	@Schema(description = "关键字")
	private String keyword;

	@Schema(description = "用户id")
	private Long userId;

	@TableField("process_template_id")
	private Long processTemplateId;

	@Schema(description = "审批类型id")
	private Long processTypeId;

	private String createTimeBegin;
	private String createTimeEnd;

	@Schema(description = "状态（0：默认 1：审批中 2：审批通过 -1：驳回）")
	private Integer status;


}