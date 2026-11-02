package com.lautung.oa.framework.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Rewrites the free-form members of the structured (logstash) log output through
 * {@link PhoneNumberMasker}, so a CN mobile number never reaches console or container stdout in
 * full — no matter which logger wrote it. Wired via {@code logging.structured.json.customizer}.
 */
public class PhoneNumberMaskingCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.applyingValueProcessor(stringProcessorFor("message"));
        members.applyingValueProcessor(stringProcessorFor("stack_trace"));
    }

    private static JsonWriter.ValueProcessor<String> stringProcessorFor(String member) {
        return JsonWriter.ValueProcessor.of(String.class, PhoneNumberMasker::mask)
                .whenHasUnescapedPath(member);
    }
}
