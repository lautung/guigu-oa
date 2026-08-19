package com.atguigu.process.service.impl;

import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.mapper.OaProcessRecordMapper;
import com.atguigu.process.service.OaProcessRecordService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 审批记录 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-15
 */
@Service
public class OaProcessRecordServiceImpl extends ServiceImpl<OaProcessRecordMapper, ProcessRecord> implements OaProcessRecordService {

    @Override
    public void record(Long processId, Integer status, String description, SysUser operator) {
        ProcessRecord processRecord = new ProcessRecord();
        processRecord.setProcessId(processId);
        processRecord.setStatus(status);
        processRecord.setDescription(description);
        processRecord.setOperateUser(operator.getName());
        processRecord.setOperateUserId(operator.getId());
        baseMapper.insert(processRecord);
    }
}
