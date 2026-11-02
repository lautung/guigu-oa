package com.lautung.oa.observability;

import com.lautung.oa.framework.observability.PhoneNumberMasker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Masking semantics: CN mobile-shaped digit runs become 138****1234, everything else passes through. */
public class PhoneNumberMaskerTest {

    @Test
    public void nullAndEmptyPassThrough() {
        assertNull(PhoneNumberMasker.mask(null));
        assertSame("", PhoneNumberMasker.mask(""));
    }

    @Test
    public void plainMobileIsMasked() {
        assertEquals("138****1234", PhoneNumberMasker.mask("13800131234"));
        assertEquals("199****0000", PhoneNumberMasker.mask("19912340000"));
    }

    @Test
    public void mobileInsideMessageIsMasked() {
        assertEquals("login audit event=failed username=138****1234 source=127.0.0.1",
                PhoneNumberMasker.mask("login audit event=failed username=13800131234 source=127.0.0.1"));
    }

    @Test
    public void multipleOccurrencesAreAllMasked() {
        assertEquals("138****1234 159****6789", PhoneNumberMasker.mask("13800131234 15912346789"));
    }

    @Test
    public void digitRunsWithinLongerNumbersAreNotMasked() {
        // 10-digit, 12-digit and prefixed runs are not CN mobile numbers and must stay intact.
        assertEquals("1380013123", PhoneNumberMasker.mask("1380013123"));
        assertEquals("138001312345", PhoneNumberMasker.mask("138001312345"));
        assertEquals("913800131234", PhoneNumberMasker.mask("913800131234"));
        assertEquals("138001312341", PhoneNumberMasker.mask("138001312341"));
    }

    @Test
    public void nonMobileLeadingDigitIsNotMasked() {
        assertEquals("12345678901", PhoneNumberMasker.mask("12345678901"));
        assertEquals("10345678901", PhoneNumberMasker.mask("10345678901"));
    }

    @Test
    public void countryCodedPrefixIsMasked() {
        // TokenLoginFilter 的 username 直取用户输入：带国家码的失败登录也会进审计行。
        assertEquals("+86138****1234", PhoneNumberMasker.mask("+8613800131234"));
    }

    @Test
    public void dashAndSpaceSeparatedFormsAreMasked() {
        assertEquals("138****1234", PhoneNumberMasker.mask("138-0013-1234"));
        assertEquals("138****1234", PhoneNumberMasker.mask("138 0013 1234"));
    }

    @Test
    public void textWithoutDigitsPassesThrough() {
        assertEquals("Business request failed with code 201", PhoneNumberMasker.mask("Business request failed with code 201"));
    }
}
