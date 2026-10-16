package com.atguigu.process.service.impl;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessTemplatePublication;
import com.atguigu.model.process.ProcessType;
import com.atguigu.process.ApprovalTemplateSupport;
import com.atguigu.process.ProcessDefinitionStorage;
import com.atguigu.process.mapper.OaProcessTemplateMapper;
import com.atguigu.process.mapper.OaProcessTemplatePublicationMapper;
import com.atguigu.process.service.ApprovalTemplate;
import com.atguigu.process.service.OaProcessTemplateService;
import com.atguigu.process.service.OaProcessTypeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

@Service
public class OaProcessTemplateServiceImpl extends ServiceImpl<OaProcessTemplateMapper, ProcessTemplate>
        implements OaProcessTemplateService {

    private static final String PUBLISHED_UNAVAILABLE = "暂不可发起，需核实原审批内容";

    @Autowired
    private OaProcessTypeService processTypeService;

    @Autowired
    private OaProcessTemplatePublicationMapper publicationMapper;

    @Autowired
    private ProcessDefinitionStorage processDefinitionStorage;

    @Autowired
    private RepositoryService repositoryService;

    @Override
    public IPage<ProcessTemplate> findPage(Page<ProcessTemplate> pageParam) {
        Page<ProcessTemplate> page = baseMapper.selectPage(pageParam, null);
        for (ProcessTemplate template : page.getRecords()) {
            if (template.getProcessTypeId() != null) {
                ProcessType type = processTypeService.getById(template.getProcessTypeId());
                if (type != null) template.setProcessTypeName(type.getName());
            }
            addAvailability(template);
        }
        return page;
    }

    @Override
    public ProcessTemplate read(Long id) {
        ProcessTemplate template = baseMapper.selectById(id);
        if (template != null) {
            if (template.getProcessTypeId() != null) {
                ProcessType type = processTypeService.getById(template.getProcessTypeId());
                if (type != null) template.setProcessTypeName(type.getName());
            }
            addAvailability(template);
        }
        return template;
    }

    @Override
    public ProcessTemplate findUniquePublishedByProcessDefinitionKey(String processDefinitionKey) {
        List<ProcessTemplate> matches = baseMapper.selectList(new LambdaQueryWrapper<ProcessTemplate>()
                .eq(ProcessTemplate::getProcessDefinitionKey, processDefinitionKey)
                .eq(ProcessTemplate::getStatus, ApprovalTemplateSupport.已发布));
        if (matches.size() != 1) return null;
        ProcessTemplate template = matches.get(0);
        addAvailability(template);
        return template;
    }

    @Override
    @Transactional
    public void saveUnpublished(ProcessTemplate template) {
        if (template.getId() != null) {
            ProcessTemplate existing = baseMapper.selectByIdForUpdate(template.getId());
            if (existing == null) throw new GuiguException(201, "审批模板不存在");
            if (!ApprovalTemplateSupport.canWrite(existing.getStatus())) {
                throw new GuiguException(201, "已发布的审批模板不能保存");
            }
            template.setStatus(ApprovalTemplateSupport.未发布);
            baseMapper.updateById(template);
            return;
        }
        template.setStatus(ApprovalTemplateSupport.未发布);
        baseMapper.insert(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        ProcessTemplate template = baseMapper.selectByIdForUpdate(id);
        if (template == null) throw new GuiguException(201, "审批模板不存在");
        if (!ApprovalTemplateSupport.canWrite(template.getStatus())) {
            throw new GuiguException(201, "已发布的审批模板不能再发布");
        }
        if (!StringUtils.hasText(template.getProcessDefinitionPath())
                || !StringUtils.hasText(template.getProcessDefinitionKey())) {
            throw new GuiguException(201, "流程 zip 无法让发起成立");
        }

        String artifactSha256;
        try {
            artifactSha256 = processDefinitionStorage.sha256(template.getProcessDefinitionPath());
        } catch (IOException e) {
            throw new GuiguException(201, "流程 zip 不存在或无法读取");
        }
        if (!processDefinitionStorage.isContentAddressedPath(template.getProcessDefinitionPath(), artifactSha256)) {
            throw new GuiguException(201, "流程 zip 路径必须由内容摘要确定，请重新上传流程 zip");
        }
        DeploymentResult deployed = deployVerified(
                template.getProcessDefinitionPath(), template.getProcessDefinitionKey(), artifactSha256);
        ProcessTemplatePublication publication = new ProcessTemplatePublication();
        publication.setProcessTemplateId(template.getId());
        publication.setArtifactPath(template.getProcessDefinitionPath());
        publication.setArtifactSha256(deployed.sha256());
        publication.setProcessDefinitionKey(template.getProcessDefinitionKey());
        publication.setDeploymentId(deployed.deploymentId());
        publication.setProcessDefinitionId(deployed.definitionId());
        publicationMapper.insert(publication);

        template.setStatus(ApprovalTemplateSupport.已发布);
        if (baseMapper.updateById(template) != 1) {
            throw new GuiguException(201, "审批模板发布状态写入失败");
        }
    }

    @Override
    @Transactional
    public void removeUnpublished(Long id) {
        ProcessTemplate existing = baseMapper.selectByIdForUpdate(id);
        if (existing == null) return;
        if (!ApprovalTemplateSupport.canWrite(existing.getStatus())) {
            throw new GuiguException(201, "已发布的审批模板不能删除");
        }
        baseMapper.deleteById(id);
    }

    @Override
    public List<ProcessType> findStartable() {
        LambdaQueryWrapper<ProcessTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTemplate::getStatus, ApprovalTemplateSupport.已发布);
        List<ProcessTemplate> available = list(wrapper).stream()
                .filter(this::isAvailable)
                .filter(template -> template.getProcessTypeId() != null)
                .collect(Collectors.toList());
        Map<Long, List<ProcessTemplate>> publishedByType = available.stream()
                .collect(Collectors.groupingBy(ProcessTemplate::getProcessTypeId, LinkedHashMap::new, Collectors.toList()));
        List<ProcessType> groups = new ArrayList<>();
        for (Map.Entry<Long, List<ProcessTemplate>> entry : publishedByType.entrySet()) {
            ProcessType type = processTypeService.getById(entry.getKey());
            if (type == null) continue;
            entry.getValue().forEach(this::addAvailability);
            type.setProcessTemplateList(entry.getValue());
            groups.add(type);
        }
        return groups;
    }

    @Override
    public boolean canInitiate(Long id) {
        ProcessTemplate template = baseMapper.selectById(id);
        return template != null && isAvailable(template);
    }

    @Override
    public boolean canWrite(Long id) {
        ProcessTemplate template = baseMapper.selectById(id);
        return template != null && ApprovalTemplateSupport.canWrite(template.getStatus());
    }

    @Override
    public UploadedProcessDefinition uploadProcessDefinition(MultipartFile file) throws IOException {
        ProcessDefinitionStorage.UploadedProcessDefinition uploaded = processDefinitionStorage.store(file);
        return new UploadedProcessDefinition(uploaded.processDefinitionPath(), uploaded.processDefinitionKey());
    }

    @Override
    public PublishedApproval resolveForInitiation(Long id) {
        ProcessTemplate template = baseMapper.selectById(id);
        if (template == null || !ApprovalTemplateSupport.canInitiate(template.getStatus())) {
            throw new GuiguException(201, "未发布的审批模板不能发起");
        }
        ProcessTemplatePublication publication = publicationFor(id);
        if (unavailableReason(template, publication) != null) {
            throw new GuiguException(201, PUBLISHED_UNAVAILABLE);
        }
        ProcessDefinition definition = currentDefinition(publication);
        return new PublishedApproval(template, definition.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int restorePublished() {
        List<ProcessTemplate> templates = baseMapper.selectPublishedForUpdate();
        int restored = 0;
        for (ProcessTemplate template : templates) {
            ProcessTemplatePublication publication = publicationFor(template.getId());
            String invalidReason = publicationEvidenceProblem(template, publication);
            if (invalidReason != null) {
                throw new GuiguException(201, "切库恢复停止：审批模板 " + template.getId()
                        + " " + PUBLISHED_UNAVAILABLE + "（" + invalidReason + "）");
            }
            ProcessDefinition existing = definitionById(publication.getProcessDefinitionId());
            if (existing != null) {
                if (!publication.getProcessDefinitionKey().equals(existing.getKey())
                        || (StringUtils.hasText(publication.getDeploymentId())
                        && !publication.getDeploymentId().equals(existing.getDeploymentId()))
                        || existing.isSuspended()) {
                    throw new GuiguException(201, "切库恢复停止：审批模板发布绑定与流程定义不一致");
                }
                if (!StringUtils.hasText(publication.getDeploymentId())) {
                    publication.setDeploymentId(existing.getDeploymentId());
                    if (publicationMapper.updateById(publication) != 1) {
                        throw new GuiguException(201, "切库恢复停止：审批模板发布绑定写入失败");
                    }
                    restored++;
                }
                continue;
            }
            DeploymentResult deployed = deployVerified(
                    publication.getArtifactPath(),
                    publication.getProcessDefinitionKey(),
                    publication.getArtifactSha256());
            publication.setDeploymentId(deployed.deploymentId());
            publication.setProcessDefinitionId(deployed.definitionId());
            if (publicationMapper.updateById(publication) != 1) {
                throw new GuiguException(201, "切库恢复停止：审批模板发布绑定写入失败");
            }
            restored++;
        }
        return restored;
    }

    private boolean isAvailable(ProcessTemplate template) {
        if (!ApprovalTemplateSupport.canInitiate(template.getStatus())) return false;
        ProcessTemplatePublication publication = publicationFor(template.getId());
        return unavailableReason(template, publication) == null;
    }

    private void addAvailability(ProcessTemplate template) {
        boolean available = isAvailable(template);
        template.setAvailableForInitiation(available);
        template.setAvailabilityMessage(
                !available && ApprovalTemplateSupport.canInitiate(template.getStatus()) ? PUBLISHED_UNAVAILABLE : null);
    }

    private String unavailableReason(ProcessTemplate template, ProcessTemplatePublication publication) {
        String evidenceProblem = publicationEvidenceProblem(template, publication);
        if (evidenceProblem != null) return evidenceProblem;
        return currentDefinition(publication) == null ? "已绑定的流程定义当前不可用" : null;
    }

    private String publicationEvidenceProblem(ProcessTemplate template, ProcessTemplatePublication publication) {
        if (publication == null) return "缺少原发布内容的可靠绑定";
        if (!StringUtils.hasText(publication.getArtifactPath())) return "缺少发布产物路径";
        if (!StringUtils.hasText(publication.getArtifactSha256())) return "缺少发布产物校验值";
        if (!processDefinitionStorage.isContentAddressedPath(
                publication.getArtifactPath(), publication.getArtifactSha256())) return "发布产物路径与校验值不一致";
        if (!StringUtils.hasText(publication.getProcessDefinitionKey())
                || !publication.getProcessDefinitionKey().equals(template.getProcessDefinitionKey())) return "流程定义 key 与模板记录不一致";
        try {
            if (!processDefinitionStorage.matchesSha256(publication.getArtifactPath(), publication.getArtifactSha256())) {
                return "发布流程 zip 缺失或内容已变化";
            }
        } catch (IOException | IllegalArgumentException e) {
            return "发布流程 zip 无法读取";
        }
        return null;
    }

    private ProcessDefinition currentDefinition(ProcessTemplatePublication publication) {
        if (!StringUtils.hasText(publication.getProcessDefinitionId())
                || !StringUtils.hasText(publication.getDeploymentId())) return null;
        ProcessDefinition definition = definitionById(publication.getProcessDefinitionId());
        if (definition == null
                || !publication.getProcessDefinitionKey().equals(definition.getKey())
                || !publication.getDeploymentId().equals(definition.getDeploymentId())
                || definition.isSuspended()) return null;
        return definition;
    }

    private ProcessDefinition definitionById(String definitionId) {
        if (!StringUtils.hasText(definitionId)) return null;
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId)
                .singleResult();
    }

    private ProcessTemplatePublication publicationFor(Long templateId) {
        return publicationMapper.selectOne(new LambdaQueryWrapper<ProcessTemplatePublication>()
                .eq(ProcessTemplatePublication::getProcessTemplateId, templateId));
    }

    private DeploymentResult deployVerified(String path, String key, String expectedSha256) {
        String sha256;
        try {
            sha256 = processDefinitionStorage.sha256(path);
        } catch (IOException e) {
            throw new GuiguException(201, "流程 zip 不存在或无法读取");
        }
        if (!processDefinitionStorage.isContentAddressedPath(path, sha256)
                || (expectedSha256 != null && !expectedSha256.equals(sha256))) {
            throw new GuiguException(201, "发布流程 zip 内容或路径摘要不匹配");
        }
        Deployment deployment;
        try (var input = processDefinitionStorage.open(path)) {
            if (input == null) throw new GuiguException(201, "流程 zip 不存在");
            try (var zip = new ZipInputStream(new BufferedInputStream(input))) {
                deployment = repositoryService.createDeployment()
                        .addZipInputStream(zip)
                        .name(path)
                        .deploy();
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取或部署流程 zip 失败", e);
        }
        try {
            if (!processDefinitionStorage.matchesSha256(path, sha256)) {
                throw new GuiguException(201, "发布流程 zip 内容已变化");
            }
        } catch (IOException e) {
            throw new GuiguException(201, "发布流程 zip 无法读取");
        }
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .processDefinitionKey(key)
                .list();
        if (definitions.size() != 1) {
            throw new GuiguException(201, "流程 zip 无法让指定审批模板发起");
        }
        ProcessDefinition definition = definitions.get(0);
        if (definition.isSuspended()) throw new GuiguException(201, "流程定义当前不可发起");
        return new DeploymentResult(deployment.getId(), definition.getId(), sha256);
    }

    private record DeploymentResult(String deploymentId, String definitionId, String sha256) { }
}
