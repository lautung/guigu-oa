package com.lautung.oa.process;

import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.process.ProcessTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批动作与详情：把 oa_process 账本收成合同面字段。
 * 列表行的组装在 {@code com.lautung.oa.process.read.LedgerApprovalListReader}（#181）。
 *
 * <p>动作层（ADR 0068，#287）：账本行的动作类型、文案与写入形状的唯一 owner。
 * {@code action_type} 是已处理白名单（APPROVE/REJECT）与动作语义的唯一判据；
 * {@code description} 继续唯一拥有人类可读文案（ADR 0043）。
 */
public final class ProcessApprovalSupport {

    private ProcessApprovalSupport() {
    }

    /**
     * 发起申请流转记录的固定文案：它与审批动作记录共享 status=1，只按文案区分的历史已由
     * V136 回填成 action_type 接管（已处理白名单），文案本身仍保留给时间线展示。
     */
    public static final String INITIATION_DESCRIPTION = "发起申请";

    /** 已撤回（ADR 0068）：oa_process.status 的撤回终态，撤回不可恢复。 */
    public static final int WITHDRAWN_STATUS = -2;

    /** 账本动作类型（V136 列）。OTHER 只由历史回填产生，新写入不使用。 */
    public enum ApprovalActionType {
        INITIATE, APPROVE, REJECT, TRANSFER, ADD_SIGN, URGE, WITHDRAW, OTHER
    }

    /**
     * 一次账本动作的写入形状：动作类型、该动作时审批所处状态与文案，
     * 以及动作对象（单目标动作才有 id；催办等多人集合只落顿号连接的姓名）。
     */
    public record ApprovalAction(
            ApprovalActionType actionType, Integer status, String description,
            Long targetUserId, String targetUserName) {

        public static ApprovalAction initiate() {
            return new ApprovalAction(ApprovalActionType.INITIATE, 1, INITIATION_DESCRIPTION, null, null);
        }

        public static ApprovalAction decision(Integer status) {
            return switch (status) {
                case 1 -> new ApprovalAction(ApprovalActionType.APPROVE, 1, "已通过", null, null);
                case -1 -> new ApprovalAction(ApprovalActionType.REJECT, -1, "已拒绝", null, null);
                default -> throw new IllegalArgumentException("非法审批状态：" + status);
            };
        }

        public static ApprovalAction withdraw() {
            return new ApprovalAction(ApprovalActionType.WITHDRAW, WITHDRAWN_STATUS, "已撤回", null, null);
        }

        /** 催办对象是当前全部处理人（含候选人）：一行记录，姓名顿号连接，target_user_id 留空。 */
        public static ApprovalAction urge(String targetNames) {
            return new ApprovalAction(ApprovalActionType.URGE, 1, "已催办", null, targetNames);
        }
    }

    public static void applyOutcome(Process process, Integer status, List<String> nextApproverNames, boolean hasNextTask) {
        if (hasNextTask) {
            process.setDescription(ProcessStartSupport.waitingDescription(nextApproverNames));
            process.setStatus(1);
            return;
        }
        if (status != null && status == 1) {
            process.setDescription("审批完成（通过）");
            process.setStatus(2);
            return;
        }
        process.setDescription("审批完成（拒绝）");
        process.setStatus(-1);
    }

    /** 撤回终态（applyOutcome 邻域的终态映射）：状态 -2，description 与状态档同源。 */
    public static void applyWithdrawn(Process process) {
        process.setStatus(WITHDRAWN_STATUS);
        process.setDescription("审批已撤回");
    }

    public static Map<String, Object> showPayload(
            Process process,
            List<ProcessRecord> processRecordList,
            ProcessTemplate processTemplate,
            boolean isApprove
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("process", process);
        payload.put("processRecordList", processRecordList);
        payload.put("processTemplate", processTemplate);
        payload.put("isApprove", isApprove);
        return payload;
    }
}
