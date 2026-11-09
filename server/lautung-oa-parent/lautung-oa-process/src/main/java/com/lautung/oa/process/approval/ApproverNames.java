package com.lautung.oa.process.approval;

import com.lautung.oa.process.membership.MembershipEvidence;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * 处理人名单的共享投影（W1 审查修复轮去重）：「引擎当前任务的处理人 → 显示名」的口径
 * 只有一份——每任务 assignee 优先、候选人按解析序补齐，用户名经调用方注入的解析缝转显示名。
 * 等待文案与发起/处理回写（ApprovalWriter）、业务审批摘要的当前审批人（BusinessApprovalReader）
 * 共用 {@link #of}；催办对象（ApprovalWriter.urge）在引擎名单之上补 PENDING 加签任务加签人。
 */
public final class ApproverNames {

    /** 提交标识长度上限（ADR 0032）：通用发起与业务发起共用，对齐 uk_oa_process_idempotency_key 列宽。 */
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private ApproverNames() {
    }

    /** 每任务的处理人用户名：assignee 优先，候选人补齐（候选任务尚无 assignee）。 */
    public static LinkedHashSet<String> taskUsernames(MembershipEvidence.CurrentTask task) {
        LinkedHashSet<String> usernames = new LinkedHashSet<>();
        if (StringUtils.hasText(task.assignee())) {
            usernames.add(task.assignee());
        }
        usernames.addAll(task.candidates());
        return usernames;
    }

    /** 任务清单 → 显示名序列；用户名解析由调用方注入（实现走 SysUserService，support 不直依赖 auth）。 */
    public static List<String> of(
            List<? extends MembershipEvidence.CurrentTask> tasks, Function<String, String> displayName) {
        List<String> names = new ArrayList<>();
        for (MembershipEvidence.CurrentTask task : tasks) {
            for (String username : taskUsernames(task)) {
                names.add(displayName.apply(username));
            }
        }
        return names;
    }
}
