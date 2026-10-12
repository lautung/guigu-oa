package com.atguigu.process.service;

import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.system.SysUser;
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

    void record(Long processId, Integer status, String description, SysUser operator);
}
