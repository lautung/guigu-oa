package com.lautung.oa.process.approval;

import org.flowable.common.engine.api.delegate.event.AbstractFlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.flowable.task.api.Task;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 全局 TASK_CREATED 解析监听器（ADR 0069，#285）。装配照抄抄送监听器，但语义相反：
 * {@link #isFailOnException()} 必须为 {@code true}——审批人解析失败要让事务回滚
 * （首节点=发起失败，中途=该次处理失败），绝不 warn-and-continue 生成无人任务。
 */
public class ApproverResolutionOnTaskCreated extends AbstractFlowableEventListener {

    private final Supplier<ApproverTaskAssigner> assigner;

    public ApproverResolutionOnTaskCreated(Supplier<ApproverTaskAssigner> assigner) {
        this.assigner = assigner;
    }

    @Override
    public Collection<? extends FlowableEventType> getTypes() {
        return Set.of(FlowableEngineEventType.TASK_CREATED);
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEvent entityEvent)
                || !(entityEvent.getEntity() instanceof Task task)) {
            return;
        }
        assigner.get().applyOnTaskCreated(new ApproverTaskAssigner.Trigger(
                task.getProcessDefinitionId(),
                task.getTaskDefinitionKey(),
                task.getId(),
                task.getProcessInstanceId() == null ? null : businessKeyOf(task)));
    }

    private String businessKeyOf(Task task) {
        // businessKey 从执行树取；取不到（理论不应发生）交给 assigner 按缺失发起记录显式失败。
        var execution = org.flowable.engine.impl.util.CommandContextUtil.getExecutionEntityManager()
                .findById(task.getExecutionId());
        return execution == null ? null : execution.getProcessInstanceBusinessKey();
    }

    @Override
    public boolean isFailOnException() {
        return true;
    }
}
