package com.lautung.oa.process.approval;

import com.lautung.oa.process.membership.MembershipEvidence;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 审批对流程引擎的读写 port。写入命令与读取证据共用同一个实现；本 interface 不暴露 Flowable 类型，
 * 实现负责把引擎任务映射成 port 记录。{@code CurrentTask} 由父接口定义，全系统只有一份。
 */
public interface ApprovalEngine extends MembershipEvidence {

    String start(String processDefinitionId, String businessKey, Map<String, Object> variables);

    void complete(String taskId);

    /**
     * 处理动作本身认领（ADR 0069）：任务尚无 assignee 时把处理人写为 assignee；
     * 已有 assignee 时不动（认领冲突在 complete 上显式失败）。
     */
    void claim(String taskId, String username);

    void rejectToEnd(String taskId);

    /**
     * 取消整个运行时实例（ADR 0068 撤回）：唯一入口，历史落 {@code STATE_=cancelled}。
     * 实例已不存在（并发对手已结束它）时实现抛 {@code FlowableObjectNotFoundException}。
     */
    void cancel(String processInstanceId, String reason);

    /**
     * 转办的引擎命令（ADR 0068，#288）：把任务永久改派给目标用户；转办人自己的候选身份
     * 一并移除（转办经手人只读，不得借候选身份继续处理）。任务已被并发对手处理掉时实现
     * 抛 {@code FlowableObjectNotFoundException}。
     */
    void reassign(String taskId, String fromUsername, String toUsername);

    /**
     * 待处理第一/二源（ADR 0068 修订 ADR 0045）：assignee 或候选人之一的任务
     * （Flowable {@code taskCandidateOrAssigned}）。
     */
    List<PendingTask> pendingTasks(String username, int firstResult, int maxResults);

    long pendingCount(String username);

    record PendingTask(String taskId, String processInstanceId, String businessKey,
                       String assignee, Date createTime) {
    }
}
