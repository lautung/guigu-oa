package com.lautung.oa.process.approval;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.process.publication.ApproverSidecar;
import org.flowable.engine.TaskService;
import org.flowable.engine.RuntimeService;
import org.flowable.identitylink.api.IdentityLinkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * TASK_CREATED 时按部署里冻结的 sidecar 给任务下发审批人（ADR 0069，#285）。
 * 只在命中规则时动作：单一审批人直接 setAssignee（覆盖 BPMN assignee——规则优先），
 * 多人清掉 BPMN assignee 后按候选用户下发；未命中节点 / 无 sidecar 的部署一律 no-op。
 *
 * <p>解析失败抛 {@link BusinessException}，监听器 isFailOnException=true，事务回滚——
 * 与抄送的 warn-and-continue 不同，绝不静默生成无人任务。
 */
@Component
public class ApproverTaskAssigner {

    private static final Logger log = LoggerFactory.getLogger(ApproverTaskAssigner.class);

    private final ApproverSidecarReader sidecarReader;
    private final ApproverResolver resolver;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final JdbcTemplate jdbcTemplate;

    public ApproverTaskAssigner(
            ApproverSidecarReader sidecarReader,
            ApproverResolver resolver,
            TaskService taskService,
            RuntimeService runtimeService,
            JdbcTemplate jdbcTemplate) {
        this.sidecarReader = sidecarReader;
        this.resolver = resolver;
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** TASK_CREATED 的触发记录：监听器只负责抽取，语义都在这里。 */
    public record Trigger(String processDefinitionId, String taskDefinitionKey, String taskId, String businessKey) {
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyOnTaskCreated(Trigger trigger) {
        if (!StringUtils.hasText(trigger.processDefinitionId()) || !StringUtils.hasText(trigger.taskDefinitionKey())) {
            return;
        }
        List<ApproverSidecar.SidecarRule> rules = sidecarReader.frozenRules(trigger.processDefinitionId());
        ApproverSidecar.SidecarRule rule = rules.stream()
                .filter(candidate -> trigger.taskDefinitionKey().equals(candidate.taskDefinitionKey()))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            // 没配规则的节点沿用 BPMN assignee（规则覆盖 assignee，反之不接管）。
            return;
        }
        String initiatorUsername = initiatorUsername(trigger.businessKey());
        ApproverResolver.ResolvedApprovers resolved = resolver.resolve(rule, initiatorUsername);
        if (resolved.candidate()) {
            // 规则覆盖 BPMN assignee：多人来源清掉写死的 assignee，按候选用户下发。
            var task = taskService.createTaskQuery().taskId(trigger.taskId()).singleResult();
            if (task != null && StringUtils.hasText(task.getAssignee())) {
                taskService.setAssignee(trigger.taskId(), null);
            }
            resolved.usernames().forEach(username ->
                    taskService.addUserIdentityLink(trigger.taskId(), username, IdentityLinkType.CANDIDATE));
            log.info("审批任务按候选用户下发 taskDefinitionKey={} taskId={} candidates={}",
                    trigger.taskDefinitionKey(), trigger.taskId(), resolved.usernames());
        } else {
            taskService.setAssignee(trigger.taskId(), resolved.usernames().get(0));
            log.info("审批任务已解析指派 taskDefinitionKey={} taskId={} assignee={}",
                    trigger.taskDefinitionKey(), trigger.taskId(), resolved.usernames().get(0));
        }
    }

    /** 发起人用户名：businessKey=oa_process.id → user_id → username（发起动作都经 ApprovalWriter）。 */
    private String initiatorUsername(String businessKey) {
        if (!StringUtils.hasText(businessKey)) {
            throw new BusinessException(201, "审批人无法解析：审批缺少发起记录");
        }
        long processId;
        try {
            processId = Long.parseLong(businessKey.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(201, "审批人无法解析：审批缺少发起记录");
        }
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT user_id FROM oa_process WHERE id = ?", Long.class, processId);
        if (userIds.isEmpty() || userIds.get(0) == null) {
            throw new BusinessException(201, "审批人无法解析：审批缺少发起记录");
        }
        List<String> usernames = jdbcTemplate.queryForList(
                "SELECT username FROM sys_user WHERE id = ?", String.class, userIds.get(0));
        if (usernames.isEmpty()) {
            throw new BusinessException(201, "审批人无法解析：发起人不存在");
        }
        return usernames.get(0);
    }
}
