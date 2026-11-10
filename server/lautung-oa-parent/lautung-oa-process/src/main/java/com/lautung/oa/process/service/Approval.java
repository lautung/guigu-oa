package com.lautung.oa.process.service;

import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.cc.CarbonCopyRecipient;
import com.lautung.oa.vo.process.AddSignVo;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.ProcessFormVo;
import com.lautung.oa.vo.process.TransferVo;

import java.util.List;
import java.util.Map;

/**
 * 办事动作。调用方传入用户；本 interface 不读 ThreadLocal。
 * 写入动作成功时返回 outcome；失败仍抛 {@code BusinessException}，不改变 HTTP 响应形状。
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

    /**
     * 撤回（ADR 0068，#287）：仅申请人、仅审批中；成功后状态 -2 已撤回，不可恢复。
     */
    WithdrawOutcome withdraw(SysUser user, Long processId);

    /**
     * 催办（ADR 0068，#287）：仅申请人、仅审批中；24 小时内最多一次；不改状态。
     */
    UrgeOutcome urge(SysUser user, Long processId);

    /**
     * 转办（ADR 0068，#288）：当前处理人把审批永久交给一个启用用户；原处理人成为转办经手人（只读）。
     */
    TransferOutcome transfer(SysUser user, Long processId, TransferVo transferVo);

    /**
     * 加签（ADR 0068，#288）：当前处理人指定 1..N 个加签人（前加签先由加签人处理；
     * 后加签原处理人的通过先记账、批次全部通过后才完成当前节点）。
     */
    AddSignOutcome addSign(SysUser user, Long processId, AddSignVo addSignVo);

    /**
     * 转办/加签候选目标（#288）：启用用户、排除申请人；排除本人由客户端本地过滤，
     * 可见性由写前校验兜底（与抄送 carbonCopyRecipients 同约定）。
     */
    List<CarbonCopyRecipient> actionTargets(Long processId);

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

    record WithdrawOutcome(Long processId, int status) {
    }

    record UrgeOutcome(Long processId) {
    }

    record TransferOutcome(Long processId, int status) {
    }

    record AddSignOutcome(Long processId, int batchNo, List<Long> signTaskIds) {
        public AddSignOutcome {
            signTaskIds = signTaskIds == null ? List.of() : List.copyOf(signTaskIds);
        }
    }
}
