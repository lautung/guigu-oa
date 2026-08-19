package com.atguigu.process.service.impl;

import com.atguigu.model.process.Process;
import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessQueryVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * 审批流程服务实现类
 * 注意：本版本已移除 Activiti 依赖（迁移至 Boot 4 / JDK 21），
 * 流程引擎集成待后续版本完成；当前仅保留基础查询能力。
 */
@Service
public class OaProcessServiceImpl extends ServiceImpl<OaProcessMapper, Process> implements OaProcessService {

    @Override
    public IPage<ProcessVo> selectPage(Page<ProcessVo> pageParam, ProcessQueryVo processQueryVo) {
        return baseMapper.selectProcessPage(pageParam, processQueryVo);
    }

    @Override
    public void deployByZip(String deployPath) {
        throw new UnsupportedOperationException("流程引擎功能待重新集成");
    }

    @Override
    public void startUp(ProcessFormVo processFormVo) {
        throw new UnsupportedOperationException("流程引擎功能待重新集成");
    }

    @Override
    public IPage<ProcessVo> findfindPending(Page<Process> pageParam) {
        return new Page<>();
    }

    @Override
    public Map<String, Object> show(Long id) {
        return Collections.emptyMap();
    }

    @Override
    public void approve(ApprovalVo approvalVo) {
        throw new UnsupportedOperationException("流程引擎功能待重新集成");
    }

    @Override
    public IPage<ProcessVo> findProcessed(Page<Process> pageParam) {
        return new Page<>();
    }

    @Override
    public IPage<ProcessVo> findStarted(Page<ProcessVo> pageParam) {
        return new Page<>();
    }
}
