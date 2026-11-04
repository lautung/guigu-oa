package com.lautung.oa.security.login;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 被限流挡下的注册只走应用日志（ADR 0062），日志行必须带 {@code kind} 才能区分
 * login / register（PR #260 审查）。
 */
class Slf4jLoginAuditTest {

    @Test
    void writesTheEventKindOnTheApplicationLogLine() {
        Logger logger = (Logger) LoggerFactory.getLogger(Slf4jLoginAudit.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new Slf4jLoginAudit().record(new LoginEvent(
                    LoginEventType.FAILURE, "reg-user", "203.0.113.9", "test-agent",
                    "注册太频繁", LoginEventKind.REGISTER));

            assertEquals(1, appender.list.size());
            String line = appender.list.get(0).getFormattedMessage();
            assertTrue(line.contains("event=failed"), line);
            assertTrue(line.contains("kind=register"), line);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
