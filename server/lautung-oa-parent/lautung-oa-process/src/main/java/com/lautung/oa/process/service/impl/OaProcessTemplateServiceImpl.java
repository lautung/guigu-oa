package com.lautung.oa.process.service.impl;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.process.OaBusinessTemplate;
import com.lautung.oa.model.process.ProcessApproverRule;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.process.ProcessTemplatePublication;
import com.lautung.oa.model.process.ProcessType;
import com.lautung.oa.process.ApprovalTemplateSupport;
import com.lautung.oa.process.ProcessDefinitionStorage;
import com.lautung.oa.process.deployment.ProcessDeploymentEngine;
import com.lautung.oa.process.mapper.OaBusinessTemplateMapper;
import com.lautung.oa.process.mapper.OaProcessTemplateMapper;
import com.lautung.oa.process.mapper.OaProcessTemplatePublicationMapper;
import com.lautung.oa.process.publication.ApproverSidecar;
import com.lautung.oa.process.publication.BpmnUserTasks;
import com.lautung.oa.process.publication.PublicationVerifier;
import com.lautung.oa.process.service.ApprovalTemplate;
import com.lautung.oa.process.service.OaProcessApproverRuleService;
import com.lautung.oa.process.service.OaProcessTemplateService;
import com.lautung.oa.process.service.OaProcessTypeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private ProcessDeploymentEngine deploymentEngine;

    @Autowired
    private PublicationVerifier publicationVerifier;

    @Autowired
    private OaProcessApproverRuleService approverRuleService;

    @Autowired
    private OaBusinessTemplateMapper businessTemplateMapper;

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
            if (existing == null) throw new BusinessException(201, "审批模板不存在");
            if (!ApprovalTemplateSupport.canWrite(existing.getStatus())) {
                throw new BusinessException(201, "已发布的审批模板不能保存");
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
        if (template == null) throw new BusinessException(201, "审批模板不存在");
        if (!ApprovalTemplateSupport.canWrite(template.getStatus())) {
            throw new BusinessException(201, "已发布的审批模板不能再发布");
        }
        if (!StringUtils.hasText(template.getProcessDefinitionPath())
                || !StringUtils.hasText(template.getProcessDefinitionKey())) {
            throw new BusinessException(201, "流程 zip 无法让发起成立");
        }

        String uploadedSha256;
        try {
            uploadedSha256 = processDefinitionStorage.sha256(template.getProcessDefinitionPath());
        } catch (IOException e) {
            throw new BusinessException(201, "流程 zip 不存在或无法读取");
        }
        if (!processDefinitionStorage.isContentAddressedPath(template.getProcessDefinitionPath(), uploadedSha256)) {
            throw new BusinessException(201, "流程 zip 路径必须由内容摘要确定，请重新上传流程 zip");
        }

        // 发布校验（ADR 0069）：解析 BPMN userTask，验「每节点至少一个来源（规则或 assignee）」
        // 与规则目标存在且启用；解析不出 BPMN 的 zip 在这里拒绝。
        List<BpmnUserTasks.UserTaskRef> userTasks;
        try {
            userTasks = BpmnUserTasks.parse(
                    uploadedZipBytes(template.getProcessDefinitionPath()), template.getProcessDefinitionKey());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(201, e.getMessage() + "，无法发布");
        }
        List<ProcessApproverRule> rules = approverRuleService.findRules(id);
        approverRuleService.validateForPublish(userTasks, rules);

        // 发布产物（ADR 0003/0046 修订）：有规则时把规则冻结成 oa-approvers.json sidecar，
        // 与上传 zip 合成 content-addressed 发布产物，证据指向合成件；上传原件保留但不再是
        // 发布产物。无规则时发布产物就是上传原件（旧模板形状不变，监听器 no-op）。
        String artifactPath = template.getProcessDefinitionPath();
        if (!rules.isEmpty()) {
            byte[] synthesized;
            try {
                synthesized = ApproverSidecar.synthesize(
                        uploadedZipBytes(template.getProcessDefinitionPath()),
                        template.getProcessDefinitionKey(),
                        sidecarRules(rules, userTasks));
                artifactPath = processDefinitionStorage
                        .store(template.getProcessDefinitionKey() + ".zip", synthesized)
                        .processDefinitionPath();
            } catch (IOException e) {
                throw new BusinessException(201, "发布产物合成失败：流程 zip 无法读取");
            }
        }
        String artifactSha256;
        try {
            artifactSha256 = processDefinitionStorage.sha256(artifactPath);
        } catch (IOException e) {
            throw new BusinessException(201, "发布产物不存在或无法读取");
        }
        ProcessDeploymentEngine.DeploymentResult deployed = deploymentEngine.deployVerified(
                artifactPath, template.getProcessDefinitionKey(), artifactSha256);
        ProcessTemplatePublication publication = new ProcessTemplatePublication();
        publication.setProcessTemplateId(template.getId());
        publication.setArtifactPath(artifactPath);
        publication.setArtifactSha256(deployed.sha256());
        publication.setProcessDefinitionKey(template.getProcessDefinitionKey());
        publication.setDeploymentId(deployed.deploymentId());
        publication.setProcessDefinitionId(deployed.definitionId());
        publicationMapper.insert(publication);

        template.setStatus(ApprovalTemplateSupport.已发布);
        if (baseMapper.updateById(template) != 1) {
            throw new BusinessException(201, "审批模板发布状态写入失败");
        }
    }

    /** 规则行 → 冻结 sidecar 行；order 取该节点在 BPMN 里的步序（1 起始，失败文案用）。 */
    private static List<ApproverSidecar.SidecarRule> sidecarRules(
            List<ProcessApproverRule> rules, List<BpmnUserTasks.UserTaskRef> userTasks) {
        List<ApproverSidecar.SidecarRule> sidecarRules = new ArrayList<>(rules.size());
        for (ProcessApproverRule rule : rules) {
            int order = 0;
            for (int index = 0; index < userTasks.size(); index++) {
                if (userTasks.get(index).key().equals(rule.getTaskDefinitionKey())) {
                    order = index + 1;
                    break;
                }
            }
            sidecarRules.add(new ApproverSidecar.SidecarRule(
                    rule.getTaskDefinitionKey(), rule.getSourceType(), rule.getTargetId(), order));
        }
        return sidecarRules;
    }

    private byte[] uploadedZipBytes(String relativePath) {
        try {
            java.io.InputStream content = processDefinitionStorage.open(relativePath);
            if (content == null) {
                throw new BusinessException(201, "流程 zip 不存在或无法读取");
            }
            return content.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException(201, "流程 zip 不存在或无法读取");
        }
    }

    @Override
    @Transactional
    public void removeUnpublished(Long id) {
        ProcessTemplate existing = baseMapper.selectByIdForUpdate(id);
        if (existing == null) return;
        if (!ApprovalTemplateSupport.canWrite(existing.getStatus())) {
            throw new BusinessException(201, "已发布的审批模板不能删除");
        }
        approverRuleService.removeAll(id);
        baseMapper.deleteById(id);
    }

    @Override
    public List<ProcessType> findStartable() {
        // 通用「发起审批」只保留无专用单据的模板（ADR 0066）：业务单据从域内发起页走
        // business key，不出现在这里。映射表是小表，一次取回模板 id 集合。
        Set<Long> businessTemplateIds = businessTemplateMapper.selectList(null).stream()
                .map(OaBusinessTemplate::getProcessTemplateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        LambdaQueryWrapper<ProcessTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTemplate::getStatus, ApprovalTemplateSupport.已发布);
        List<ProcessTemplate> available = list(wrapper).stream()
                .filter(this::startable)
                .filter(template -> !businessTemplateIds.contains(template.getId()))
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
    public boolean canWrite(Long id) {
        ProcessTemplate template = baseMapper.selectById(id);
        return template != null && ApprovalTemplateSupport.canWrite(template.getStatus());
    }

    @Override
    public UploadedProcessDefinition uploadProcessDefinition(MultipartFile file) throws IOException {
        ProcessDefinitionStorage.UploadedProcessDefinition uploaded = processDefinitionStorage.store(file);
        // 解析不出 BPMN 的 zip 允许保存（ADR 0069）：节点清单为空，发布时拒绝。
        List<BpmnUserTasks.UserTaskRef> userTasks = List.of();
        try {
            java.io.InputStream content = processDefinitionStorage.open(uploaded.processDefinitionPath());
            if (content != null) {
                userTasks = BpmnUserTasks.parse(
                        content.readAllBytes(), uploaded.processDefinitionKey());
            }
        } catch (IllegalArgumentException | IOException unparsable) {
            userTasks = List.of();
        }
        return new UploadedProcessDefinition(
                uploaded.processDefinitionPath(), uploaded.processDefinitionKey(), userTasks);
    }

    @Override
    public PublishedApproval resolveForInitiation(Long id) {
        ProcessTemplate template = baseMapper.selectById(id);
        if (template == null || !ApprovalTemplateSupport.canInitiate(template.getStatus())) {
            throw new BusinessException(201, "未发布的审批模板不能发起");
        }
        ProcessTemplatePublication publication = publicationFor(id);
        if (!bindingComplete(publication)) {
            throw new BusinessException(201, PUBLISHED_UNAVAILABLE);
        }
        PublicationVerifier.Verdict verdict = publicationVerifier.verify(template, publication);
        if (!verdict.usable()) {
            throw new BusinessException(201, PUBLISHED_UNAVAILABLE);
        }
        return new PublishedApproval(template, verdict.definition().id());
    }

    /**
     * 切库恢复（ADR 0003）：编排完全由发布判定 verdict 驱动——证据问题停止；
     * 绑定不一致停止；可用且部署绑定空则按记录 definitionId 查得的引擎定义回填
     * （可靠依据非猜测）；定义缺失经 port 重部署。停止文案逐字，批量全部成功才生效。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int restorePublished() {
        List<ProcessTemplate> templates = baseMapper.selectPublishedForUpdate();
        int restored = 0;
        for (ProcessTemplate template : templates) {
            ProcessTemplatePublication publication = publicationFor(template.getId());
            PublicationVerifier.Verdict verdict = publicationVerifier.verify(template, publication);
            if (verdict.evidenceProblem() != null) {
                throw new BusinessException(201, "切库恢复停止：审批模板 " + template.getId()
                        + " " + PUBLISHED_UNAVAILABLE + "（" + verdict.evidenceProblem() + "）");
            }
            if (verdict.binding() instanceof PublicationVerifier.Binding.Inconsistent) {
                throw new BusinessException(201, "切库恢复停止：审批模板发布绑定与流程定义不一致");
            }
            if (verdict.binding() instanceof PublicationVerifier.Binding.Usable usable) {
                if (!StringUtils.hasText(publication.getDeploymentId())) {
                    publication.setDeploymentId(usable.definition().deploymentId());
                    if (publicationMapper.updateById(publication) != 1) {
                        throw new BusinessException(201, "切库恢复停止：审批模板发布绑定写入失败");
                    }
                    restored++;
                }
                continue;
            }
            // Binding.DefinitionMissing：定义缺失，按发布记录经 port 重部署重取绑定。
            ProcessDeploymentEngine.DeploymentResult deployed = deploymentEngine.deployVerified(
                    publication.getArtifactPath(),
                    publication.getProcessDefinitionKey(),
                    publication.getArtifactSha256());
            publication.setDeploymentId(deployed.deploymentId());
            publication.setProcessDefinitionId(deployed.definitionId());
            if (publicationMapper.updateById(publication) != 1) {
                throw new BusinessException(201, "切库恢复停止：审批模板发布绑定写入失败");
            }
            restored++;
        }
        return restored;
    }

    /**
     * 读侧归并（ADR 0003）：证据问题与绑定问题由 verdict 区分，对外同一条
     * 「暂不可发起，需核实原审批内容」；记录完整性先经 {@link #bindingComplete} 把关。
     */
    private boolean startable(ProcessTemplate template) {
        if (!ApprovalTemplateSupport.canInitiate(template.getStatus())) return false;
        ProcessTemplatePublication publication = publicationFor(template.getId());
        return bindingComplete(publication)
                && publicationVerifier.verify(template, publication).usable();
    }

    /**
     * 发布记录完整性守卫：已记录的部署绑定仍需完整——发布即写入 deploymentId，
     * 缺失说明记录不完整，恢复补齐前不可发起；verdict 对此判「可用」是给切库
     * 恢复回填用的，不是给发起用的。
     */
    private boolean bindingComplete(ProcessTemplatePublication publication) {
        return publication != null && StringUtils.hasText(publication.getDeploymentId());
    }

    private void addAvailability(ProcessTemplate template) {
        boolean available = startable(template);
        template.setAvailableForInitiation(available);
        template.setAvailabilityMessage(
                !available && ApprovalTemplateSupport.canInitiate(template.getStatus()) ? PUBLISHED_UNAVAILABLE : null);
    }

    private ProcessTemplatePublication publicationFor(Long templateId) {
        return publicationMapper.selectOne(new LambdaQueryWrapper<ProcessTemplatePublication>()
                .eq(ProcessTemplatePublication::getProcessTemplateId, templateId));
    }
}
