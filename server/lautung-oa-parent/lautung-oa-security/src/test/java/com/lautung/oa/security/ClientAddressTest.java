package com.lautung.oa.security;

import com.lautung.oa.security.login.ClientAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientAddressTest {

    @Test
    public void theProxyWrittenRealIpWinsOverAClientSuppliedForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Real-IP", "203.0.113.7");
        request.addHeader("X-Forwarded-For", "198.51.100.9, 203.0.113.7");

        assertEquals("203.0.113.7", ClientAddress.of(request));
    }

    @Test
    public void theNearestForwardedForHopIsTheAddressTheProxySaw() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.9, 203.0.113.7");

        assertEquals("203.0.113.7", ClientAddress.of(request));
    }

    @Test
    public void anInvalidRealIpFallsBackToTheNearestForwardedHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Real-IP", "not-an-address");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("203.0.113.7", ClientAddress.of(request));
    }

    @Test
    public void anInvalidForwardedForFallsBackToTheRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Forwarded-For", "not-an-address");

        assertEquals("172.18.0.5", ClientAddress.of(request));
    }

    @Test
    public void withoutProxyHeadersTheRemoteAddressIsUsed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");

        assertEquals("172.18.0.5", ClientAddress.of(request));
    }

    @Test
    public void blankAndMissingAddressesNeverProduceABlankKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");

        assertEquals("unknown", ClientAddress.of(request));
    }
}
