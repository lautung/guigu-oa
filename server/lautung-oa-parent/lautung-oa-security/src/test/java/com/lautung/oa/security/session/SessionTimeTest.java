package com.lautung.oa.security.session;

import com.lautung.oa.model.monitor.SessionTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTimeTest {

    @Test
    void formatsAndParsesTheSingleStorageShape() {
        SessionTime time = SessionTime.parse("2026-10-29 08:05:09");

        assertEquals(LocalDateTime.of(2026, 10, 29, 8, 5, 9), time.value());
        assertEquals("2026-10-29 08:05:09", time.toString());
        assertEquals(time, SessionTime.parse(" 2026-10-29 08:05:09 "));
    }

    @Test
    void blankOrMalformedTextParsesToNullInsteadOfFailingTheRead() {
        assertNull(SessionTime.parse(null));
        assertNull(SessionTime.parse(" "));
        assertNull(SessionTime.parse("2026-10-29"));
        assertNull(SessionTime.parse("2026-13-01 00:00:00"));
    }

    @Test
    void strictParsingRejectsDaysThatDoNotExistInsteadOfRollingThemOver() {
        // SMART resolver 会把 2 月 30 日改成 2 月 28 日；会话时间不允许这种悄悄改日。
        assertNull(SessionTime.parse("2026-02-30 00:00:00"));
        assertNull(SessionTime.parse("2026-04-31 00:00:00"));
        assertNull(SessionTime.parse("2026-02-29 00:00:00"));
        assertEquals(LocalDateTime.of(2024, 2, 29, 0, 0),
                SessionTime.parse("2024-02-29 00:00:00").value());
    }

    @Test
    void comparesChronologically() {
        SessionTime earlier = SessionTime.parse("2026-10-29 08:00:00");
        SessionTime later = SessionTime.parse("2026-10-29 09:00:00");

        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);
        assertEquals(0, earlier.compareTo(SessionTime.parse("2026-10-29 08:00:00")));
    }

    @Test
    void nowIsInTheSameShape() {
        assertTrue(SessionTime.now().toString().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void rejectsAMissingInstant() {
        assertThrows(NullPointerException.class, () -> new SessionTime(null));
    }
}
