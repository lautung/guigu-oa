package com.atguigu.process.cc;

public record CarbonCopyTrigger(
        String processDefinitionKey,
        String taskDefinitionKey,
        String taskId,
        String businessKey) {
}
