package com.atguigu.process.service.impl;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.common.result.ResultCodeEnum;
import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.ProcessApprovalSupport;
import com.atguigu.process.ProcessEngineApproval;
import com.atguigu.process.approval.ApprovalWriter;
import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.process.membership.ApprovalMembership;
import com.atguigu.process.membership.ApprovalMembershipResolver;
import com.atguigu.process.service.OaProcessRecordService;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.process.service.Approval;
import com.atguigu.process.service.ApprovalTemplate;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessQueryVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OaProcessServiceImpl extends ServiceImpl<OaProcessMapper, Process> implements OaProcessService {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ApprovalTemplate processTemplateService;

    @Autowired
    private OaProcessRecordService processRecordService;

    @Autowired
    private ApprovalMembershipResolver membershipResolver;

    @Autowired
    private ApprovalWriter approvalWriter;

    @Override
    public IPage<ProcessVo> selectPage(Page<ProcessVo> pageParam, ProcessQueryVo processQueryVo) {
        return baseMapper.selectProcessPage(pageParam, processQueryVo);
    }

    @Override
    public Approval.StartOutcome startUp(SysUser user, ProcessFormVo processFormVo) {
        return approvalWriter.startUp(user, processFormVo);
    }

    @Override
    public IPage<ProcessVo> findPending(SysUser user, Page<Process> pageParam) {
        String username = user.getUsername();
        int firstResult = (int) ((pageParam.getCurrent() - 1) * pageParam.getSize());
        int maxResults = (int) pageParam.getSize();
        List<Task> taskList = ProcessEngineApproval.pendingTasks(taskService, username, firstResult, maxResults);
        long totalCount = ProcessEngineApproval.pendingCount(taskService, username);

        List<ProcessVo> processVoList = new ArrayList<>();
        for (Task task : taskList) {
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
            if (processInstance == null || !StringUtils.hasText(processInstance.getBusinessKey())) {
                continue;
            }
            Process process = baseMapper.selectById(Long.parseLong(processInstance.getBusinessKey()));
            if (process == null) {
                continue;
            }
            processVoList.add(ProcessApprovalSupport.toPendingVo(process, task.getId()));
        }
        IPage<ProcessVo> page = new Page<>(pageParam.getCurrent(), pageParam.getSize(), totalCount);
        page.setRecords(processVoList);
        return page;
    }

    @Override
    public Map<String, Object> show(SysUser user, Long id) {
        ReadableApproval readable = requireReadableApproval(id, user);
        Process process = readable.process();
        LambdaQueryWrapper<ProcessRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessRecord::getProcessId, id);
        List<ProcessRecord> processRecordList = processRecordService.list(wrapper);
        ProcessTemplate processTemplate = processTemplateService.read(process.getProcessTemplateId());
        return ProcessApprovalSupport.showPayload(
                process, processRecordList, processTemplate, readable.membership().isCurrentApprover());
    }

    @Override
    public Process requireApprovalMember(Long processId, SysUser user) {
        return requireReadableApproval(processId, user).process();
    }

    private ReadableApproval requireReadableApproval(Long processId, SysUser user) {
        if (processId == null || user == null || user.getId() == null) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        Process process = baseMapper.selectById(processId);
        if (process == null) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        ApprovalMembership membership = membershipResolver.resolve(user, process);
        if (!membership.canRead()) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        return new ReadableApproval(process, membership);
    }

    private record ReadableApproval(Process process, ApprovalMembership membership) {
    }

    @Override
    public Approval.DecisionOutcome approve(SysUser user, ApprovalVo approvalVo) {
        return approvalWriter.approve(user, approvalVo);
    }

    @Override
    public IPage<ProcessVo> findProcessed(SysUser user, Page<Process> pageParam) {
        String username = user.getUsername();
        int firstResult = (int) ((pageParam.getCurrent() - 1) * pageParam.getSize());
        int maxResults = (int) pageParam.getSize();
        List<HistoricTaskInstance> historicTasks =
                ProcessEngineApproval.processedTasks(historyService, username, firstResult, maxResults);
        long totalCount = ProcessEngineApproval.processedCount(historyService, username);

        List<ProcessVo> processVoList = new ArrayList<>();
        for (HistoricTaskInstance historicTask : historicTasks) {
            LambdaQueryWrapper<Process> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Process::getProcessInstanceId, historicTask.getProcessInstanceId());
            Process process = baseMapper.selectOne(wrapper);
            if (process == null) {
                continue;
            }
            processVoList.add(ProcessApprovalSupport.toProcessedVo(process));
        }
        IPage<ProcessVo> pageModel = new Page<>(pageParam.getCurrent(), pageParam.getSize(), totalCount);
        pageModel.setRecords(processVoList);
        return pageModel;
    }

    @Override
    public IPage<ProcessVo> findStarted(SysUser user, Page<ProcessVo> pageParam) {
        ProcessQueryVo processQueryVo = new ProcessQueryVo();
        processQueryVo.setUserId(user.getId());
        return selectPage(pageParam, processQueryVo);
    }
}
