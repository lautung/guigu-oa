package com.lautung.oa.model.process;

import com.lautung.oa.model.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 加签任务（ADR 0068，#288）：与引擎任务配对的待处理行，加签不注入 BPMN。
 * 类型与状态的取值语义见
 * {@link com.lautung.oa.process.ProcessApprovalSupport.AddSignType} /
 * {@link com.lautung.oa.process.ProcessApprovalSupport.AddSignStatus}。
 */
@Data
@Schema(description = "ProcessAddSignTask")
@TableName("oa_process_add_sign_task")
public class ProcessAddSignTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "审批流程id")
    @TableField("process_id")
    private Long processId;

    @Schema(description = "配对的引擎任务id")
    @TableField("engine_task_id")
    private String engineTaskId;

    @Schema(description = "同一引擎任务内的批次序号（从 1 递增）")
    @TableField("batch_no")
    private Integer batchNo;

    @Schema(description = "加签类型（BEFORE 前加签 / AFTER 后加签）")
    @TableField("sign_type")
    private String signType;

    @Schema(description = "加签人用户id")
    @TableField("assignee_id")
    private Long assigneeId;

    @Schema(description = "状态（PENDING/APPROVED/REJECTED/DESTROYED）")
    @TableField("status")
    private String status;
}
