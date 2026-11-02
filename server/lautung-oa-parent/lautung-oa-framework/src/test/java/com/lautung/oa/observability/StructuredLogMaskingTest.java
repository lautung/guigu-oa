package com.lautung.oa.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.OutputStreamAppender;
import com.lautung.oa.framework.observability.PhoneNumberMaskingCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Encoder-level contract: with the masking customizer wired through
 * {@code logging.structured.json.customizer}, the logstash-encoded console output never contains a
 * full CN mobile number — neither in the message member nor in the stack trace member. Without the
 * customizer the raw number is written (control), so the assertions below prove the customizer is
 * what masks.
 */
public class StructuredLogMaskingTest {

    private static final String RAW = "13800138000";
    private static final String MASKED = "138****8000";

    @Test
    public void encoderWithoutCustomizerWritesRawNumber() {
        String json = logThroughEncoder(null, logger -> logger.info("login attempt for {}", RAW));
        assertTrue(json.contains(RAW));
    }

    @Test
    public void messageMemberIsMaskedWhenCustomizerIsWired() {
        String json = logThroughEncoder(maskingEnvironment(),
                logger -> logger.info("login attempt for {}", RAW));
        assertTrue(json.contains(MASKED));
        assertFalse(json.contains(RAW));
    }

    @Test
    public void loginAuditUsernameShapedAsPhoneIsMasked() {
        String json = logThroughEncoder(maskingEnvironment(), logger -> logger.info(
                "login audit event=failed username=" + RAW + " source=127.0.0.1 failures=1"));
        assertTrue(json.contains("username=138****8000"));
        assertFalse(json.contains(RAW));
    }

    @Test
    public void stackTraceMemberIsMaskedWhenExceptionCarriesPhone() {
        String json = logThroughEncoder(maskingEnvironment(), logger -> logger.warn(
                "Business request failed with code 201", new RuntimeException("rejected value [" + RAW + "]")));
        assertTrue(json.contains("138****8000"));
        assertFalse(json.contains(RAW));
    }

    @Test
    public void nonSensitiveFieldsSurviveMasking() {
        String json = logThroughEncoder(maskingEnvironment(), logger -> logger.info(
                "request completed method={} status={}", "GET", 200));
        assertTrue(json.contains("\"logger_name\":\"audit\""));
        assertTrue(json.contains("\"level\":\"INFO\""));
        assertTrue(json.contains("\"@timestamp\""));
        assertTrue(json.contains("\"message\":\"request completed method=GET status=200\""));
    }

    @Test
    public void mdcPairsPassThroughUnmasked() {
        String json = logThroughEncoder(maskingEnvironment(), logger -> {
            logger.getLoggerContext().getMDCAdapter().put("traceId", "t-83-correlation");
            logger.info("login attempt for {}", RAW);
        });
        assertTrue(json.contains("\"traceId\":\"t-83-correlation\""));
        assertTrue(json.contains(MASKED));
    }

    private static String logThroughEncoder(Environment environment, Consumer<Logger> emit) {
        // A hand-built LoggerContext has no MDC adapter until a real SLF4J provider wires it;
        // the structured formatter reads MDC pairs and would NPE without one.
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putObject(Environment.class.getName(),
                environment == null ? new StandardEnvironment() : environment);
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("logstash");
        encoder.start();

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(captured);
        appender.start();

        Logger logger = context.getLogger("audit");
        logger.addAppender(appender);
        logger.setAdditive(false);
        emit.accept(logger);
        appender.stop();
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static StandardEnvironment maskingEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("masking", Map.of(
                "logging.structured.json.customizer", PhoneNumberMaskingCustomizer.class.getName())));
        return environment;
    }
}
