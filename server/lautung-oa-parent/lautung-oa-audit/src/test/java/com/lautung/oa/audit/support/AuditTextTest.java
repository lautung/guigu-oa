package com.lautung.oa.audit.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditTextTest {

    @Test
    void keepsValuesWithinTheLimitUnchanged() {
        assertEquals("abc", AuditText.truncate("abc", 10));
        assertEquals("0123456789", AuditText.truncate("0123456789", 10));
    }

    @Test
    void truncatesToExactlyTheLimitWithAMarker() {
        String truncated = AuditText.truncate("x".repeat(50), 20);

        assertEquals(20, truncated.length());
        assertTrue(truncated.endsWith(AuditText.TRUNCATION_MARKER), truncated);
        assertTrue(truncated.startsWith("x"), truncated);
    }

    @Test
    void passesNullThrough() {
        assertNull(AuditText.truncate(null, 10));
    }
}
