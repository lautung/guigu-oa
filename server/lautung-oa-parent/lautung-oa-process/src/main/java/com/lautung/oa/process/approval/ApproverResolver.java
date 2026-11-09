package com.lautung.oa.process.approval;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.process.publication.ApproverSidecar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按冻结规则解析当前组织的审批人（ADR 0069）：冻结的是规则不是人，任务创建时按
 * 当前组织解析。解析不到显式失败（首节点=发起失败，中途=该次处理失败并指明节点），
 * 绝不静默生成无人任务、不跳过、不自动转管理员。
 *
 * <p>语义逐条对齐 ADR 0069：部门负责人=发起人所在部门的直接 leader_user_id（只认启用、
 * 不逐级向上、自审保留）；角色/岗位=启用成员集合剔除申请人（剔除后为空→解析失败）。
 * 解析结果按用户名排序，多候选等待名单的顺序确定。
 */
@Component
public class ApproverResolver {

    private static final Logger log = LoggerFactory.getLogger(ApproverResolver.class);

    private final JdbcTemplate jdbcTemplate;

    public ApproverResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 解析结果：单一审批人（candidate=false，直接指派）或候选用户集合（candidate=true）。 */
    public record ResolvedApprovers(List<String> usernames, boolean candidate) {

        public static ResolvedApprovers assignee(String username) {
            return new ResolvedApprovers(List.of(username), false);
        }

        public static ResolvedApprovers candidates(List<String> usernames) {
            return new ResolvedApprovers(List.copyOf(usernames), true);
        }
    }

    public ResolvedApprovers resolve(ApproverSidecar.SidecarRule rule, String initiatorUsername) {
        ApproverSourceType sourceType = ApproverSourceType.of(rule.sourceType());
        if (sourceType == null) {
            fail(rule, null, "sidecar 来源类型无效：" + rule.sourceType());
        }
        return switch (sourceType) {
            case DEPT_LEADER -> resolveDeptLeader(rule, initiatorUsername);
            case ROLE -> resolveMembers(rule, initiatorUsername, ApproverSourceType.ROLE,
                    """
                    SELECT DISTINCT u.username FROM sys_user u
                    JOIN sys_user_role ur ON ur.user_id = u.id
                    WHERE ur.role_id = ? AND ur.is_deleted = 0
                      AND u.status = 1 AND u.is_deleted = 0
                    ORDER BY u.username
                    """,
                    roleAlive(rule));
            case POST -> resolveMembers(rule, initiatorUsername, ApproverSourceType.POST,
                    """
                    SELECT DISTINCT u.username FROM sys_user u
                    JOIN sys_user_post up ON up.user_id = u.id
                    WHERE up.post_id = ? AND up.is_deleted = 0
                      AND u.status = 1 AND u.is_deleted = 0
                    ORDER BY u.username
                    """,
                    postAlive(rule));
        };
    }

    /** 部门负责人：发起人所在部门的直接 leader_user_id，只认启用、不逐级向上、自审保留。 */
    private ResolvedApprovers resolveDeptLeader(ApproverSidecar.SidecarRule rule, String initiatorUsername) {
        if (!StringUtils.hasText(initiatorUsername)) {
            fail(rule, ApproverSourceType.DEPT_LEADER, "发起人缺失");
        }
        List<Long> initiatorRows = jdbcTemplate.queryForList(
                "SELECT dept_id FROM sys_user WHERE username = ? AND status = 1 AND is_deleted = 0",
                Long.class, initiatorUsername);
        if (initiatorRows.isEmpty()) {
            fail(rule, ApproverSourceType.DEPT_LEADER, "发起人不存在或已停用");
        }
        Long deptId = initiatorRows.get(0);
        if (deptId == null) {
            fail(rule, ApproverSourceType.DEPT_LEADER, "发起人没有所属部门");
        }
        List<Long> deptRows = jdbcTemplate.queryForList(
                "SELECT leader_user_id FROM sys_dept WHERE id = ? AND status = 1 AND is_deleted = 0",
                Long.class, deptId);
        if (deptRows.isEmpty()) {
            fail(rule, ApproverSourceType.DEPT_LEADER, "发起人所在部门不存在或已停用");
        }
        Long leaderUserId = deptRows.get(0);
        if (leaderUserId == null) {
            fail(rule, ApproverSourceType.DEPT_LEADER, "部门没有负责人");
        }
        List<String> leaders = jdbcTemplate.queryForList(
                "SELECT username FROM sys_user WHERE id = ? AND status = 1 AND is_deleted = 0",
                String.class, leaderUserId);
        if (leaders.isEmpty()) {
            // 负责人停用或删除视为无负责人（ADR 0069），修好组织后重试。
            fail(rule, ApproverSourceType.DEPT_LEADER, "部门负责人不存在或已停用");
        }
        // 部门负责人就是申请人时保留自审：组织里没有上级数据，硬失败会让负责人无法提交任何申请。
        return ResolvedApprovers.assignee(leaders.get(0));
    }

    /** 角色/岗位：启用成员集合剔除申请人；剔除后为空→解析失败（自审回避）。 */
    private ResolvedApprovers resolveMembers(
            ApproverSidecar.SidecarRule rule,
            String initiatorUsername,
            ApproverSourceType sourceType,
            String memberSql,
            Integer targetAlive) {
        if (targetAlive == null || targetAlive == 0) {
            fail(rule, sourceType, "目标不存在或已停用");
        }
        List<String> members = jdbcTemplate.queryForList(memberSql, String.class, rule.targetId());
        Set<String> candidates = new LinkedHashSet<>(members);
        candidates.remove(initiatorUsername);
        if (candidates.isEmpty()) {
            fail(rule, sourceType, "成员剔除申请人后无人可审");
        }
        return ResolvedApprovers.candidates(List.copyOf(candidates));
    }

    private Integer roleAlive(ApproverSidecar.SidecarRule rule) {
        if (rule.targetId() == null) {
            return null;
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role WHERE id = ? AND is_deleted = 0",
                Integer.class, rule.targetId());
    }

    private Integer postAlive(ApproverSidecar.SidecarRule rule) {
        if (rule.targetId() == null) {
            return null;
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_post WHERE id = ? AND status = 1 AND is_deleted = 0",
                Integer.class, rule.targetId());
    }

    /** 失败文案（ADR 0069）：有步序用「第 N 步」，缺步序退回节点 id，都指明来源。 */
    private static String failureMessage(ApproverSidecar.SidecarRule rule, ApproverSourceType sourceType) {
        String label = sourceType == null ? "未知来源" : sourceType.label();
        return rule.order() >= 1
                ? "第 " + rule.order() + " 步审批人无法解析（" + label + "）"
                : "审批节点[" + rule.taskDefinitionKey() + "]审批人无法解析（" + label + "）";
    }

    /** 命名失败：对外文案逐字按 ADR 0069；内部原因进结构化日志供排查组织数据缺口。 */
    private void fail(ApproverSidecar.SidecarRule rule, ApproverSourceType sourceType, String reason) {
        log.warn("审批人解析失败 sourceType={} ruleTargetId={} order={} reason={}",
                sourceType, rule.targetId(), rule.order(), reason);
        throw new BusinessException(201, failureMessage(rule, sourceType));
    }
}
