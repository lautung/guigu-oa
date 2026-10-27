package com.atguigu.process.service;

import com.atguigu.model.process.Process;
import com.atguigu.model.system.SysUser;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 办事六个动作。调用方传入用户；本 interface 不读 ThreadLocal。
 * 两个写入动作成功时返回 outcome；失败仍抛 {@code GuiguException}，不改变 HTTP 响应形状。
 */
public interface Approval {

    StartOutcome startUp(SysUser user, ProcessFormVo processFormVo);

    IPage<ProcessVo> findPending(SysUser user, Page<Process> pageParam);

    Map<String, Object> show(SysUser user, Long id);

    /**
     * Return an approval after checking that the user is its applicant, active/past approver, or CC recipient.
     * Related capabilities such as attachment credential issuance must use the same
     * {@link com.atguigu.process.membership.ApprovalMembership}.
     */
    Process requireApprovalMember(Long processId, SysUser user);

    DecisionOutcome approve(SysUser user, ApprovalVo approvalVo);

    IPage<ProcessVo> findProcessed(SysUser user, Page<Process> pageParam);

    IPage<ProcessVo> findStarted(SysUser user, Page<ProcessVo> pageParam);

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
