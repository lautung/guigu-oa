package com.lautung.oa.process.cc;

public record CarbonCopyTrigger(
        String processDefinitionKey,
        String taskDefinitionKey,
        String taskId,
        String businessKey) {
}
