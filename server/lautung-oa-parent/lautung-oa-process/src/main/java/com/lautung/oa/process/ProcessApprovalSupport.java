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
 */
public final class ProcessApprovalSupport {

    private ProcessApprovalSupport() {
    }

    /**
     * 发起申请流转记录的固定文案：它与审批动作记录（{@link #recordDescription}）共享 status=1，
     * 只按文案区分（V107 订正同一事实）；「已处理」读按它排除发起记录（#186，ADR 0049）。
     */
    public static final String INITIATION_DESCRIPTION = "发起申请";

    public static String recordDescription(Integer status) {
        return switch (status) {
            case 1 -> "已通过";
            case -1 -> "已拒绝";
            default -> throw new IllegalArgumentException("非法审批状态：" + status);
        };
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
