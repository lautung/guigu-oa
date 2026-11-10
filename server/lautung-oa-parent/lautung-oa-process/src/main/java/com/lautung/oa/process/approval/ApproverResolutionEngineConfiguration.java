package com.lautung.oa.process.approval;

import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态审批人解析监听器的引擎装配（照抄抄送 {@code cc/CarbonCopyEngineConfiguration} 的样板）。
 * 语义差异在监听器自身：{@code isFailOnException=true}，解析失败回滚事务。
 */
@Configuration
public class ApproverResolutionEngineConfiguration {

    @Bean
    public ProcessEngineConfigurationConfigurer approverResolutionEventListenerConfigurer(
            ObjectProvider<ApproverTaskAssigner> assignerProvider) {
        return engineConfiguration -> {
            List<FlowableEventListener> listeners = new ArrayList<>();
            if (engineConfiguration.getEventListeners() != null) {
                listeners.addAll(engineConfiguration.getEventListeners());
            }
            listeners.add(new ApproverResolutionOnTaskCreated(assignerProvider::getObject));
            engineConfiguration.setEventListeners(listeners);
        };
    }
}
