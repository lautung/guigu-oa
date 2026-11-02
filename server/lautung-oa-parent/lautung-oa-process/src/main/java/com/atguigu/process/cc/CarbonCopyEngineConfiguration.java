package com.atguigu.process.cc;

import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CarbonCopyEngineConfiguration {

    @Bean
    public ProcessEngineConfigurationConfigurer carbonCopyEventListenerConfigurer(
            ObjectProvider<CarbonCopyWriter> writerProvider) {
        return engineConfiguration -> {
            List<FlowableEventListener> listeners = new ArrayList<>();
            if (engineConfiguration.getEventListeners() != null) {
                listeners.addAll(engineConfiguration.getEventListeners());
            }
            listeners.add(new CarbonCopyOnTaskCreated(writerProvider::getObject));
            engineConfiguration.setEventListeners(listeners);
        };
    }
}
