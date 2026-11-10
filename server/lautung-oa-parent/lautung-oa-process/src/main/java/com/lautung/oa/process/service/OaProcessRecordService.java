package com.lautung.oa.process.service;

import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport.ApprovalAction;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 审批记录 服务类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-15
 */
public interface OaProcessRecordService extends IService<ProcessRecord> {

    /** 唯一账本写入口：动作类型与文案由 {@link ApprovalAction} 一处拥有（ADR 0068）。 */
    void record(Long processId, ApprovalAction action, SysUser operator);
}
