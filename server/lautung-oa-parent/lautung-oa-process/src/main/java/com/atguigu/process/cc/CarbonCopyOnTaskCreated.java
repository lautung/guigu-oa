package com.atguigu.process.cc;

import org.flowable.common.engine.api.delegate.event.AbstractFlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

/** Writes business-table grants when a user task is created, without changing BPMN. */
public class CarbonCopyOnTaskCreated extends AbstractFlowableEventListener {

    private static final Logger log = LoggerFactory.getLogger(CarbonCopyOnTaskCreated.class);
    private final Supplier<CarbonCopyWriter> writer;

    public CarbonCopyOnTaskCreated(Supplier<CarbonCopyWriter> writer) {
        this.writer = writer;
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

        String processDefinitionKey = ProcessDefinitionUtil
                .getProcessDefinition(task.getProcessDefinitionId())
                .getKey();
        DelegateExecution execution = CommandContextUtil.getExecutionEntityManager()
                .findById(task.getExecutionId());
        String businessKey = execution == null ? null : execution.getProcessInstanceBusinessKey();
        try {
            writer.get().copyOnTaskCreated(new CarbonCopyTrigger(
                    processDefinitionKey, task.getTaskDefinitionKey(), task.getId(), businessKey));
        } catch (RuntimeException exception) {
            log.warn("抄送规则处理失败 processDefinitionKey={} taskDefinitionKey={} taskId={}",
                    processDefinitionKey, task.getTaskDefinitionKey(), task.getId(), exception);
        }
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }
}
