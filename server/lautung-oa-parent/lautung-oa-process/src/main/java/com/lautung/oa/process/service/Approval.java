package com.lautung.oa.process.service;

import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.ProcessFormVo;

import java.util.List;
import java.util.Map;

/**
 * 办事动作。调用方传入用户；本 interface 不读 ThreadLocal。
 * 两个写入动作成功时返回 outcome；失败仍抛 {@code BusinessException}，不改变 HTTP 响应形状。
 * 列表读不在这里：五种审批列表走 {@link com.lautung.oa.process.read.ApprovalListReader}（#181）。
 */
public interface Approval {

    StartOutcome startUp(SysUser user, ProcessFormVo processFormVo);

    Map<String, Object> show(SysUser user, Long id);

    /**
     * Return an approval after checking that the user is its applicant, active/past approver, or CC recipient.
     * Related capabilities such as attachment credential issuance must use the same
     * {@link com.lautung.oa.process.membership.ApprovalMembership}.
     */
    Process requireApprovalMember(Long processId, SysUser user);

    DecisionOutcome approve(SysUser user, ApprovalVo approvalVo);

    record StartOutcome(Long processId, String processInstanceId, List<String> currentTaskIds) {
        public StartOutcome {
            currentTaskIds = currentTaskIds == null ? List.of() : List.copyOf(currentTaskIds);
        }
    }

    record DecisionOutcome(Long processId, int status, List<String> nextTaskIds) {
        public DecisionOutcome {
            nextTaskIds = nextTaskIds == null ? List.of() : List.copyOf(nextTaskIds);
        }
    }
}
