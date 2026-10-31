package com.atguigu.process;

import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.vo.process.ProcessVo;
import org.springframework.beans.BeanUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待处理 / 已处理 / 审批动作：把引擎任务与 oa_process 账本收成合同面字段。
 */
public final class ProcessApprovalSupport {

    private ProcessApprovalSupport() {
    }

    public static ProcessVo toPendingVo(Process process, String taskId) {
        ProcessVo vo = new ProcessVo();
        BeanUtils.copyProperties(process, vo);
        vo.setTaskId(taskId);
        return vo;
    }

    public static ProcessVo toProcessedVo(Process process) {
        return toPendingVo(process, null);
    }

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
