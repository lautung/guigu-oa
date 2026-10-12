package com.atguigu.process.service.impl;

import com.atguigu.auth.service.SysUserService;
import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.common.result.ResultCodeEnum;
import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.ProcessApprovalSupport;
import com.atguigu.process.ProcessDefinitionStorage;
import com.atguigu.process.ProcessEngineApproval;
import com.atguigu.process.ProcessStartSupport;
import com.atguigu.process.attachment.AttachmentService;
import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.process.service.OaProcessRecordService;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.process.service.OaProcessTemplateService;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessQueryVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
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
import java.util.Objects;
import java.util.zip.ZipInputStream;

@Service
public class OaProcessServiceImpl extends ServiceImpl<OaProcessMapper, Process> implements OaProcessService {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ProcessDefinitionStorage processDefinitionStorage;

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

    @Autowired
    private AttachmentService attachmentService;

    @Override
    public IPage<ProcessVo> selectPage(Page<ProcessVo> pageParam, ProcessQueryVo processQueryVo) {
        return baseMapper.selectProcessPage(pageParam, processQueryVo);
    }

    @Override
    public void deployByZip(String deployPath) {
        deployByZip(deployPath, null);
    }

    @Override
    public void deployByZip(String deployPath, String processDefinitionKey) {
        InputStream inputStream;
        try {
            inputStream = processDefinitionStorage.open(deployPath);
        } catch (IOException e) {
            throw new IllegalStateException("读取流程 zip 失败: " + deployPath, e);
        }
        if (inputStream == null) {
            throw new IllegalArgumentException("流程 zip 不存在: " + deployPath);
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            Deployment deployment = repositoryService.createDeployment()
                    .addZipInputStream(zipInputStream)
                    .name(deployPath)
                    .deploy();
            if (StringUtils.hasText(processDefinitionKey)
                    && repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .processDefinitionKey(processDefinitionKey)
                    .count() == 0) {
                repositoryService.deleteDeployment(deployment.getId());
                throw new GuiguException(201, "流程 zip 无法让发起成立");
            }
        } catch (IOException e) {
            throw new IllegalStateException("部署流程 zip 失败: " + deployPath, e);
        }
    }

    @Override
    @Transactional
    public void startUp(SysUser user, ProcessFormVo processFormVo) {
        if (!processTemplateService.canInitiate(processFormVo.getProcessTemplateId())) {
            throw new GuiguException(201, "未发布的审批模板不能发起");
        }
        ProcessTemplate processTemplate = processTemplateService.read(processFormVo.getProcessTemplateId());

        Process process = new Process();
        BeanUtils.copyProperties(processFormVo, process);
        process.setStatus(1);
        process.setProcessCode(System.currentTimeMillis() + "");
        process.setUserId(user.getId());
        process.setFormValues(processFormVo.getFormValues());
        process.setTitle(ProcessStartSupport.title(user.getName(), processTemplate.getName()));
        baseMapper.insert(process);

        attachmentService.bindDraft(
                user, processFormVo.getAttachmentDraftId(), processFormVo.getAttachmentIds(), process.getId());

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                processTemplate.getProcessDefinitionKey(),
                String.valueOf(process.getId()),
                ProcessStartSupport.engineVariables(processFormVo.getFormValues()));

        List<String> nameList = approverNames(ProcessEngineApproval.currentTasks(taskService, processInstance.getId()));
        process.setProcessInstanceId(processInstance.getId());
        process.setDescription(ProcessStartSupport.waitingDescription(nameList));
        baseMapper.updateById(process);

        processRecordService.record(process.getId(), 1, "发起申请", user);
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
        Process process = requireApprovalMember(id, user);
        LambdaQueryWrapper<ProcessRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessRecord::getProcessId, id);
        List<ProcessRecord> processRecordList = processRecordService.list(wrapper);
        ProcessTemplate processTemplate = processTemplateService.read(process.getProcessTemplateId());

        List<String> assignees = new ArrayList<>();
        for (Task task : ProcessEngineApproval.currentTasks(taskService, process.getProcessInstanceId())) {
            if (StringUtils.hasText(task.getAssignee())) {
                assignees.add(task.getAssignee());
            }
        }
        boolean isApprove = ProcessApprovalSupport.isCurrentApprover(assignees, user.getUsername());
        return ProcessApprovalSupport.showPayload(process, processRecordList, processTemplate, isApprove);
    }

    @Override
    public Process requireApprovalMember(Long processId, SysUser user) {
        if (processId == null || user == null || user.getId() == null) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        Process process = baseMapper.selectById(processId);
        if (process == null) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        if (Objects.equals(process.getUserId(), user.getId())) {
            return process;
        }

        String username = user.getUsername();
        String processInstanceId = process.getProcessInstanceId();
        if (StringUtils.hasText(username) && StringUtils.hasText(processInstanceId)) {
            boolean isCurrentApprover = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .taskAssignee(username)
                    .count() > 0;
            boolean hasApproved = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .taskAssignee(username)
                    .count() > 0;
            if (isCurrentApprover || hasApproved) {
                return process;
            }
        }
        throw new GuiguException(ResultCodeEnum.FORBIDDEN);
    }

    @Override
    @Transactional
    public void approve(SysUser user, ApprovalVo approvalVo) {
        Process process = requireCurrentApprover(user, approvalVo);
        if (approvalVo.getStatus() != null && approvalVo.getStatus() == 1) {
            ProcessEngineApproval.complete(taskService, approvalVo.getTaskId());
        } else {
            ProcessEngineApproval.rejectToEnd(repositoryService, taskService, approvalVo.getTaskId());
        }

        processRecordService.record(
                approvalVo.getProcessId(),
                approvalVo.getStatus(),
                ProcessApprovalSupport.recordDescription(approvalVo.getStatus()),
                user);

        List<Task> nextTasks = ProcessEngineApproval.currentTasks(taskService, process.getProcessInstanceId());
        ProcessApprovalSupport.applyOutcome(
                process,
                approvalVo.getStatus(),
                approverNames(nextTasks),
                !nextTasks.isEmpty());
        baseMapper.updateById(process);
    }

    private Process requireCurrentApprover(SysUser user, ApprovalVo approvalVo) {
        if (user == null || !StringUtils.hasText(user.getUsername())
                || approvalVo == null || approvalVo.getProcessId() == null
                || !StringUtils.hasText(approvalVo.getTaskId())) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        Process process = baseMapper.selectById(approvalVo.getProcessId());
        if (process == null || !StringUtils.hasText(process.getProcessInstanceId())) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        Task task = taskService.createTaskQuery()
                .taskId(approvalVo.getTaskId())
                .processInstanceId(process.getProcessInstanceId())
                .taskAssignee(user.getUsername())
                .singleResult();
        if (task == null) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        return process;
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
