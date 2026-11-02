package com.lautung.oa.process.service;

import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.system.SysUser;
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
