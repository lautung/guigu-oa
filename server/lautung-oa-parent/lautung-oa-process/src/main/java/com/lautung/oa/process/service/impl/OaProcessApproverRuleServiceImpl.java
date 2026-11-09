package com.lautung.oa.process.service.impl;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.process.ProcessApproverRule;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.process.ApprovalTemplateSupport;
import com.lautung.oa.process.ProcessDefinitionStorage;
import com.lautung.oa.process.approval.ApproverSourceType;
import com.lautung.oa.process.cc.CarbonCopyRecipient;
import com.lautung.oa.process.mapper.OaProcessApproverRuleMapper;
import com.lautung.oa.process.mapper.OaProcessTemplateMapper;
import com.lautung.oa.process.publication.BpmnUserTasks;
import com.lautung.oa.process.service.OaProcessApproverRuleService;
import com.lautung.oa.vo.process.ApproverRuleRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OaProcessApproverRuleServiceImpl implements OaProcessApproverRuleService {

    private final OaProcessApproverRuleMapper ruleMapper;
    private final OaProcessTemplateMapper templateMapper;
    private final ProcessDefinitionStorage processDefinitionStorage;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public OaProcessApproverRuleServiceImpl(
            OaProcessApproverRuleMapper ruleMapper,
            OaProcessTemplateMapper templateMapper,
            ProcessDefinitionStorage processDefinitionStorage,
            JdbcTemplate jdbcTemplate) {
        this.ruleMapper = ruleMapper;
        this.templateMapper = templateMapper;
        this.processDefinitionStorage = processDefinitionStorage;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ProcessApproverRule> findRules(Long templateId) {
        if (templateId == null) {
            return List.of();
        }
        return ruleMapper.selectList(new LambdaQueryWrapper<ProcessApproverRule>()
                .eq(ProcessApproverRule::getProcessTemplateId, templateId)
                .orderByAsc(ProcessApproverRule::getTaskDefinitionKey));
    }

    @Override
    public List<BpmnUserTasks.UserTaskRef> findUserTasks(ProcessTemplate template) {
        if (template == null || !StringUtils.hasText(template.getProcessDefinitionPath())) {
            return List.of();
        }
        try {
            return BpmnUserTasks.parse(readZip(template.getProcessDefinitionPath()),
                    template.getProcessDefinitionKey());
        } catch (IllegalArgumentException unparsable) {
            // 解析不出 BPMN 的 zip 可以保存（配置页拿不到节点清单），发布时拒绝。
            return List.of();
        }
    }

    @Override
    public RuleConfig readConfig(Long templateId) {
        ProcessTemplate template = requireTemplate(templateId);
        return new RuleConfig(findUserTasks(template), findRules(templateId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceRules(Long templateId, List<ApproverRuleRequest> requests) {
        ProcessTemplate template = requireTemplate(templateId);
        if (!ApprovalTemplateSupport.canWrite(template.getStatus())) {
            throw new BusinessException(201, "已发布的审批模板不能修改审批人规则");
        }
        List<ApproverRuleRequest> lines = requests == null ? List.of() : requests;
        List<BpmnUserTasks.UserTaskRef> userTasks = findUserTasks(template);
        Map<String, String> nodeNames = nodeNames(userTasks);

        // 每节点单选来源：重复节点先于逐行目标校验暴露。
        Set<String> seenKeys = new HashSet<>();
        for (ApproverRuleRequest request : lines) {
            if (!StringUtils.hasText(request.getTaskDefinitionKey())) {
                throw new BusinessException(201, "审批节点不能为空");
            }
            if (!seenKeys.add(request.getTaskDefinitionKey())) {
                throw new BusinessException(201, "审批节点[" + request.getTaskDefinitionKey() + "]重复配置");
            }
        }

        List<ProcessApproverRule> rules = new ArrayList<>();
        for (ApproverRuleRequest request : lines) {
            String key = request.getTaskDefinitionKey();
            if (!nodeNames.containsKey(key)) {
                throw new BusinessException(201, "审批节点[" + key + "]不存在于流程 zip");
            }
            ApproverSourceType sourceType = ApproverSourceType.of(request.getSourceType());
            if (sourceType == null) {
                throw new BusinessException(201, "审批人来源类型无效");
            }
            Long targetId = requireTarget(sourceType, key, nodeNames.get(key), request.getTargetId());
            ProcessApproverRule rule = new ProcessApproverRule();
            rule.setProcessTemplateId(templateId);
            rule.setTaskDefinitionKey(key);
            rule.setSourceType(sourceType.name());
            rule.setTargetId(targetId);
            rules.add(rule);
        }
        ruleMapper.deleteByTemplateId(templateId);
        rules.forEach(ruleMapper::insert);
    }

    @Override
    public void validateForPublish(List<BpmnUserTasks.UserTaskRef> userTasks, List<ProcessApproverRule> rules) {
        Map<String, BpmnUserTasks.UserTaskRef> taskIndex = userTasks.stream()
                .collect(Collectors.toMap(BpmnUserTasks.UserTaskRef::key, Function.identity(), (first, second) -> first));
        Map<String, String> nodeNames = nodeNames(userTasks);

        for (BpmnUserTasks.UserTaskRef task : userTasks) {
            boolean coveredByRule = rules.stream()
                    .anyMatch(rule -> task.key().equals(rule.getTaskDefinitionKey()));
            if (!coveredByRule && !StringUtils.hasText(task.assignee())) {
                throw new BusinessException(201, "审批节点[" + displayName(task) + "]缺少审批人来源（配置审批人规则或流程 assignee）");
            }
        }
        for (ProcessApproverRule rule : rules) {
            String key = rule.getTaskDefinitionKey();
            BpmnUserTasks.UserTaskRef task = taskIndex.get(key);
            if (task == null) {
                throw new BusinessException(201, "审批节点[" + key + "]不存在于流程 zip");
            }
            ApproverSourceType sourceType = ApproverSourceType.of(rule.getSourceType());
            requireTarget(sourceType, key, nodeNames.get(key), rule.getTargetId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAll(Long templateId) {
        ruleMapper.deleteByTemplateId(templateId);
    }

    /**
     * 来源目标的存在性与启用校验（保存与发布共用同一道门）：部门负责人按发起人
     * 所在部门解析，不指定目标；角色必须存在未删；岗位必须存在且启用。
     */
    private Long requireTarget(
            ApproverSourceType sourceType, String key, String nodeName, Long targetId) {
        String node = nodeName != null ? nodeName : key;
        return switch (sourceType) {
            case DEPT_LEADER -> {
                if (targetId != null) {
                    throw new BusinessException(201, "部门负责人来源按发起人所在部门解析，不指定目标");
                }
                yield null;
            }
            case ROLE -> {
                if (targetId == null || count("SELECT COUNT(*) FROM sys_role WHERE id = ? AND is_deleted = 0", targetId) == 0) {
                    throw new BusinessException(201, "审批节点[" + node + "]的角色不存在或已停用");
                }
                yield targetId;
            }
            case POST -> {
                if (targetId == null
                        || count("SELECT COUNT(*) FROM sys_post WHERE id = ? AND status = 1 AND is_deleted = 0", targetId) == 0) {
                    throw new BusinessException(201, "审批节点[" + node + "]的岗位不存在或已停用");
                }
                yield targetId;
            }
        };
    }

    private ProcessTemplate requireTemplate(Long templateId) {
        if (templateId == null) {
            throw new BusinessException(201, "审批模板不存在");
        }
        ProcessTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(201, "审批模板不存在");
        }
        return template;
    }

    private Map<String, String> nodeNames(List<BpmnUserTasks.UserTaskRef> userTasks) {
        return userTasks.stream()
                .collect(Collectors.toMap(BpmnUserTasks.UserTaskRef::key,
                        task -> task.name() == null ? task.key() : task.name(),
                        (first, second) -> first));
    }

    private static String displayName(BpmnUserTasks.UserTaskRef task) {
        return StringUtils.hasText(task.name()) ? task.name() : task.key();
    }

    private int count(String sql, Long id) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return result == null ? 0 : result;
    }

    private byte[] readZip(String relativePath) {
        try {
            var content = processDefinitionStorage.open(relativePath);
            if (content == null) {
                throw new IllegalArgumentException(BpmnUserTasks.NO_BPMN_MESSAGE);
            }
            return content.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException(BpmnUserTasks.NO_BPMN_MESSAGE, e);
        }
    }
}
