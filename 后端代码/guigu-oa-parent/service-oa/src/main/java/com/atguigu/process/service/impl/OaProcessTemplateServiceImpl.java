package com.atguigu.process.service.impl;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessType;
import com.atguigu.process.ApprovalTemplateSupport;
import com.atguigu.process.ProcessCutoverSupport;
import com.atguigu.process.mapper.OaProcessTemplateMapper;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.process.service.OaProcessTemplateService;
import com.atguigu.process.service.OaProcessTypeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 审批模板 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-14
 */
@Service
public class OaProcessTemplateServiceImpl extends ServiceImpl<OaProcessTemplateMapper, ProcessTemplate> implements OaProcessTemplateService {

    @Autowired
    private OaProcessTypeService processTypeService;

    @Autowired
    private OaProcessService processService;

    @Override
    public IPage<ProcessTemplate> findPage(Page<ProcessTemplate> pageParam) {
        Page<ProcessTemplate> processTemplatePage = baseMapper.selectPage(pageParam, null);
        for (ProcessTemplate processTemplate : processTemplatePage.getRecords()) {
            Long processTypeId = processTemplate.getProcessTypeId();
            LambdaQueryWrapper<ProcessType> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProcessType::getId, processTypeId);
            ProcessType processType = processTypeService.getOne(wrapper);
            if (processType == null) {
                continue;
            }
            processTemplate.setProcessTypeName(processType.getName());
        }
        return processTemplatePage;
    }

    @Override
    public ProcessTemplate read(Long id) {
        return baseMapper.selectById(id);
    }

    @Override
    public void saveUnpublished(ProcessTemplate template) {
        if (template.getId() != null) {
            ProcessTemplate existing = baseMapper.selectById(template.getId());
            if (existing != null && !ApprovalTemplateSupport.canWrite(existing.getStatus())) {
                throw new GuiguException(201, "已发布的审批模板不能保存");
            }
        }
        template.setStatus(ApprovalTemplateSupport.未发布);
        if (template.getId() == null) {
            save(template);
        } else {
            updateById(template);
        }
    }

    @Override
    public void publish(Long id) {
        ProcessTemplate processTemplate = baseMapper.selectById(id);
        if (processTemplate == null) {
            throw new GuiguException(201, "审批模板不存在");
        }
        if (ApprovalTemplateSupport.canInitiate(processTemplate.getStatus())) {
            throw new GuiguException(201, "已发布的审批模板不能再发布");
        }
        if (!StringUtils.hasText(processTemplate.getProcessDefinitionPath())
                || !StringUtils.hasText(processTemplate.getProcessDefinitionKey())) {
            throw new GuiguException(201, "流程 zip 无法让发起成立");
        }
        processService.deployByZip(
                processTemplate.getProcessDefinitionPath(),
                processTemplate.getProcessDefinitionKey());
        processTemplate.setStatus(ApprovalTemplateSupport.已发布);
        baseMapper.updateById(processTemplate);
    }

    @Override
    public void removeUnpublished(Long id) {
        ProcessTemplate existing = baseMapper.selectById(id);
        if (existing == null) {
            return;
        }
        if (!ApprovalTemplateSupport.canWrite(existing.getStatus())) {
            throw new GuiguException(201, "已发布的审批模板不能删除");
        }
        removeById(id);
    }

    @Override
    public List<ProcessType> findStartable() {
        List<ProcessType> groups = new ArrayList<>();
        for (ProcessType processType : processTypeService.list()) {
            LambdaQueryWrapper<ProcessTemplate> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProcessTemplate::getProcessTypeId, processType.getId());
            wrapper.eq(ProcessTemplate::getStatus, ApprovalTemplateSupport.已发布);
            List<ProcessTemplate> published = list(wrapper);
            if (published.isEmpty()) {
                continue;
            }
            processType.setProcessTemplateList(published);
            groups.add(processType);
        }
        return groups;
    }

    @Override
    public boolean canInitiate(Long id) {
        ProcessTemplate template = baseMapper.selectById(id);
        return template != null && ApprovalTemplateSupport.canInitiate(template.getStatus());
    }

    @Override
    public boolean canWrite(Long id) {
        ProcessTemplate template = baseMapper.selectById(id);
        return template != null && ApprovalTemplateSupport.canWrite(template.getStatus());
    }

    @Override
    public int redeployPublished() {
        LambdaQueryWrapper<ProcessTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTemplate::getStatus, ApprovalTemplateSupport.已发布);
        List<ProcessTemplate> toRedeploy = ProcessCutoverSupport.publishedToRedeploy(list(wrapper));
        for (ProcessTemplate processTemplate : toRedeploy) {
            processService.deployByZip(processTemplate.getProcessDefinitionPath());
        }
        return toRedeploy.size();
    }

}
