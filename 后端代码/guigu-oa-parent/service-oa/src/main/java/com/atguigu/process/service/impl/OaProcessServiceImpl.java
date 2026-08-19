package com.atguigu.process.service.impl;

import com.atguigu.auth.service.SysUserService;
import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.system.SysUser;
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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
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

        List<String> nameList = new ArrayList<>();
        for (Task task : getCurrentTaskList(processInstance.getId())) {
            String assigneeName = task.getAssignee();
            if (!StringUtils.hasText(assigneeName)) {
                continue;
            }
            SysUser assignee = sysUserService.getUserByUserName(assigneeName);
            nameList.add(assignee != null && StringUtils.hasText(assignee.getName())
                    ? assignee.getName()
                    : assigneeName);
        }
        process.setProcessInstanceId(processInstance.getId());
        process.setDescription(ProcessStartSupport.waitingDescription(nameList));
        baseMapper.updateById(process);

        processRecordService.record(process.getId(), 1, "发起申请");
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

    private List<Task> getCurrentTaskList(String processInstanceId) {
        return taskService.createTaskQuery().processInstanceId(processInstanceId).list();
    }
}
