package com.atguigu.process.service;

import com.atguigu.model.process.Process;
import com.atguigu.model.system.SysUser;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 办事六个动作。调用方传入用户；本 interface 不读 ThreadLocal。
 */
public interface Approval {

    void startUp(SysUser user, ProcessFormVo processFormVo);

    IPage<ProcessVo> findPending(SysUser user, Page<Process> pageParam);

    Map<String, Object> show(SysUser user, Long id);

    /**
     * Return an approval after checking that the user is its applicant or an active/past approver.
     * Related capabilities such as attachment credential issuance must use this same gate.
     */
    Process requireApprovalMember(Long processId, SysUser user);

    void approve(SysUser user, ApprovalVo approvalVo);

    IPage<ProcessVo> findProcessed(SysUser user, Page<Process> pageParam);

    IPage<ProcessVo> findStarted(SysUser user, Page<ProcessVo> pageParam);
}
