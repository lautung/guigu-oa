package com.atguigu.process.service.impl;

import com.atguigu.auth.service.SysUserService;
import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.ProcessApprovalSupport;
import com.atguigu.process.ProcessEngineApproval;
import com.atguigu.process.ProcessStartSupport;
import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.process.service.OaProcessRecordService;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.process.service.OaProcessTemplateService;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessQueryVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

@Service
public class OaProcessServiceImpl extends ServiceImpl<OaProcessMapper, Process> implements OaProcessService {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    @Lazy
    private OaProcessTemplateService processTemplateService;

    @Autowired
    private OaProcessRecordService processRecordService;

    @Override
    public IPage<ProcessVo> selectPage(Page<ProcessVo> pageParam, ProcessQueryVo processQueryVo) {
        return baseMapper.selectProcessPage(pageParam, processQueryVo);
    }

    @Override
    public void deployByZip(String deployPath) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(deployPath);
        if (inputStream == null) {
            throw new IllegalArgumentException("流程 zip 不存在: " + deployPath);
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            repositoryService.createDeployment()
                    .addZipInputStream(zipInputStream)
                    .name(deployPath)
                    .deploy();
        } catch (IOException e) {
            throw new IllegalStateException("部署流程 zip 失败: " + deployPath, e);
        }
    }

    @Override
    @Transactional
    public void startUp(ProcessFormVo processFormVo) {
        SysUser sysUser = sysUserService.getById(LoginUserInfoHelper.getUserId());
        ProcessTemplate processTemplate = processTemplateService.getById(processFormVo.getProcessTemplateId());

        Process process = new Process();
        BeanUtils.copyProperties(processFormVo, process);
        process.setStatus(1);
        process.setProcessCode(System.currentTimeMillis() + "");
        process.setUserId(LoginUserInfoHelper.getUserId());
        process.setFormValues(processFormVo.getFormValues());
        process.setTitle(ProcessStartSupport.title(sysUser.getName(), processTemplate.getName()));
        baseMapper.insert(process);

        ensureDeployed(processTemplate);

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                processTemplate.getProcessDefinitionKey(),
                String.valueOf(process.getId()),
                ProcessStartSupport.engineVariables(processFormVo.getFormValues()));

        List<String> nameList = approverNames(ProcessEngineApproval.currentTasks(taskService, processInstance.getId()));
        process.setProcessInstanceId(processInstance.getId());
        process.setDescription(ProcessStartSupport.waitingDescription(nameList));
        baseMapper.updateById(process);

        processRecordService.record(process.getId(), 1, "发起申请");
    }

    @Override
    public IPage<ProcessVo> findfindPending(Page<Process> pageParam) {
        String username = LoginUserInfoHelper.getUsername();
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
    public Map<String, Object> show(Long id) {
        Process process = baseMapper.selectById(id);
        LambdaQueryWrapper<ProcessRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessRecord::getProcessId, id);
        List<ProcessRecord> processRecordList = processRecordService.list(wrapper);
        ProcessTemplate processTemplate = processTemplateService.getById(process.getProcessTemplateId());

        List<String> assignees = new ArrayList<>();
        for (Task task : ProcessEngineApproval.currentTasks(taskService, process.getProcessInstanceId())) {
            if (StringUtils.hasText(task.getAssignee())) {
                assignees.add(task.getAssignee());
            }
        }
        boolean isApprove = ProcessApprovalSupport.isCurrentApprover(assignees, LoginUserInfoHelper.getUsername());
        return ProcessApprovalSupport.showPayload(process, processRecordList, processTemplate, isApprove);
    }

    @Override
    @Transactional
    public void approve(ApprovalVo approvalVo) {
        if (approvalVo.getStatus() != null && approvalVo.getStatus() == 1) {
            ProcessEngineApproval.complete(taskService, approvalVo.getTaskId());
        } else {
            ProcessEngineApproval.rejectToEnd(repositoryService, taskService, approvalVo.getTaskId());
        }

        processRecordService.record(
                approvalVo.getProcessId(),
                approvalVo.getStatus(),
                ProcessApprovalSupport.recordDescription(approvalVo.getStatus()));

        Process process = baseMapper.selectById(approvalVo.getProcessId());
        List<Task> nextTasks = ProcessEngineApproval.currentTasks(taskService, process.getProcessInstanceId());
        ProcessApprovalSupport.applyOutcome(
                process,
                approvalVo.getStatus(),
                approverNames(nextTasks),
                !nextTasks.isEmpty());
        baseMapper.updateById(process);
    }

    @Override
    public IPage<ProcessVo> findProcessed(Page<Process> pageParam) {
        String username = LoginUserInfoHelper.getUsername();
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
    public IPage<ProcessVo> findStarted(Page<ProcessVo> pageParam) {
        ProcessQueryVo processQueryVo = new ProcessQueryVo();
        processQueryVo.setUserId(LoginUserInfoHelper.getUserId());
        IPage<ProcessVo> pageModel = selectPage(pageParam, processQueryVo);
        for (ProcessVo item : pageModel.getRecords()) {
            item.setTaskId("0");
        }
        return pageModel;
    }

    private void ensureDeployed(ProcessTemplate processTemplate) {
        String key = processTemplate.getProcessDefinitionKey();
        if (!StringUtils.hasText(key)) {
            return;
        }
        long count = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(key)
                .count();
        if (count == 0 && StringUtils.hasText(processTemplate.getProcessDefinitionPath())) {
            deployByZip(processTemplate.getProcessDefinitionPath());
        }
    }

    private List<String> approverNames(List<Task> taskList) {
        List<String> nameList = new ArrayList<>();
        for (Task task : taskList) {
            String assigneeName = task.getAssignee();
            if (!StringUtils.hasText(assigneeName)) {
                continue;
            }
            SysUser assignee = sysUserService.getUserByUserName(assigneeName);
            nameList.add(assignee != null && StringUtils.hasText(assignee.getName())
                    ? assignee.getName()
                    : assigneeName);
        }
        return nameList;
    }
}
