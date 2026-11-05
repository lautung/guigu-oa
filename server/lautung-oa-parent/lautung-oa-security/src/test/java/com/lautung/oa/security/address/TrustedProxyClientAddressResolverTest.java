package com.lautung.oa.security.address;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #262：只断言「给定对端、头、受信网段，resolver 返回什么」，不碰解析中间步骤。
 */
@ExtendWith(OutputCaptureExtension.class)
class TrustedProxyClientAddressResolverTest {

    private static final String PROXY = "10.0.0.0/8";

    @Test
    void withoutConfiguredProxiesForwardedHeadersAreIgnored() {
        MockHttpServletRequest request = request("172.18.0.5");
        request.addHeader("X-Real-IP", "203.0.113.7");
        request.addHeader("X-Forwarded-For", "198.51.100.9, 203.0.113.7");

        assertEquals("172.18.0.5", resolver("").resolve(request));
    }

    @Test
    void aPeerOutsideTheTrustedRangesCannotForgeARealIpOrForwardedChain() {
        MockHttpServletRequest request = request("172.18.0.5");
        request.addHeader("X-Real-IP", "203.0.113.7");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("172.18.0.5", resolver(PROXY).resolve(request));
    }

    @Test
    void theFirstNonTrustedHopFromTheRightIsTheClient() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.9, 203.0.113.7, 10.0.0.5");

        assertEquals("203.0.113.7", resolver(PROXY).resolve(request));
    }

    @Test
    void aClientPrependedFakeHopNeverWins() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.7, 10.0.0.5");

        assertEquals("203.0.113.7", resolver(PROXY).resolve(request));
    }

    @Test
    void theProxyWrittenRealIpWinsWhenTheForwardedChainIsFullyTrusted() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "10.0.0.3, 10.0.0.5");
        request.addHeader("X-Real-IP", "203.0.113.7");

        assertEquals("203.0.113.7", resolver(PROXY).resolve(request));
    }

    @Test
    void anAllTrustedChainWithoutRealIpFallsBackToThePeer() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "10.0.0.3, 10.0.0.5");

        assertEquals("10.0.0.2", resolver(PROXY).resolve(request));
    }

    @Test
    void invalidAndOversizedHopsAreSkippedWithoutFabricatingASource() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For",
                "not-an-address, " + "9".repeat(80) + ", 203.0.113.7");

        assertEquals("203.0.113.7", resolver(PROXY).resolve(request));
    }

    @Test
    void aValidChainWithOnlyInvalidHopsFallsBackToThePeer() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "not-an-address, 999.1.1.1");
        request.addHeader("X-Real-IP", "not-an-address");

        assertEquals("10.0.0.2", resolver(PROXY).resolve(request));
    }

    @Test
    void anInvalidRealIpIsIgnoredWhenTheForwardedChainHasAnAddress() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Real-IP", "not-an-address");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.5");

        assertEquals("203.0.113.7", resolver(PROXY).resolve(request));
    }

    @Test
    void portsAndIpv6BracketsAreStripped() {
        MockHttpServletRequest ipv4WithPort = request("10.0.0.2");
        ipv4WithPort.addHeader("X-Forwarded-For", "203.0.113.7:443, 10.0.0.5");

        assertEquals("203.0.113.7", resolver(PROXY).resolve(ipv4WithPort));

        MockHttpServletRequest bracketedIpv6 = request("10.0.0.2");
        bracketedIpv6.addHeader("X-Forwarded-For", "[2001:db9::7]:443");

        assertEquals("2001:db9::7", resolver(PROXY).resolve(bracketedIpv6));
    }

    @Test
    void ipv6PeersAndHopsMatchConfiguredRanges() {
        MockHttpServletRequest request = request("2001:db8::1");
        request.addHeader("X-Forwarded-For", "2001:db8::5, 2001:db9::7");

        assertEquals("2001:db9::7", resolver("2001:db8::/32").resolve(request));
    }

    @Test
    void ipv4CompatibleIpv6LiteralsAreAccepted() {
        MockHttpServletRequest compressed = request("10.0.0.2");
        compressed.addHeader("X-Forwarded-For", "::1.2.3.4");

        assertEquals("::1.2.3.4", resolver(PROXY).resolve(compressed));

        MockHttpServletRequest mapped = request("10.0.0.2");
        mapped.addHeader("X-Forwarded-For", "::ffff:203.0.113.7");

        assertEquals("::ffff:203.0.113.7", resolver(PROXY).resolve(mapped));

        MockHttpServletRequest full = request("10.0.0.2");
        full.addHeader("X-Forwarded-For", "1:2:3:4:5:6:203.0.113.7");

        assertEquals("1:2:3:4:5:6:203.0.113.7", resolver(PROXY).resolve(full));
    }

    @Test
    void anUntrustedIpv6PeerCannotForge() {
        MockHttpServletRequest request = request("2001:db9::1");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("2001:db9::1", resolver("2001:db8::/32").resolve(request));
    }

    @Test
    void headerNamesAreCaseInsensitive() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("x-forwarded-for", "203.0.113.7");

        assertEquals("203.0.113.7", resolver(PROXY).resolve(request));
    }

    @Test
    void multipleForwardedForHeaderLinesAreReadFromTheRight() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.9");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("203.0.113.7", resolver(PROXY).resolve(request));
    }

    @Test
    void aTrustedPeerWithoutForwardingHeadersUsesThePeer() {
        assertEquals("10.0.0.2", resolver(PROXY).resolve(request("10.0.0.2")));
    }

    @Test
    void blankAndMissingPeersNeverProduceABlankKey() {
        MockHttpServletRequest request = request("");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("unknown", resolver(PROXY).resolve(request));
    }

    @Test
    void invalidConfiguredRangesAreIgnoredWithAWarningInsteadOfBlockingStartup(CapturedOutput output) {
        TrustedProxyClientAddressResolver resolver = resolver("not-a-cidr, 10.0.0.0/8");
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("203.0.113.7", resolver.resolve(request));
        assertTrue(output.getOut().contains("Ignoring invalid trusted proxy entries"),
                "an invalid CIDR must warn exactly once instead of widening or breaking trust");
        assertTrue(output.getOut().contains("not-a-cidr"));
    }

    @Test
    void duplicateAndMixedFamilyRangesAreAccepted() {
        MockHttpServletRequest ipv4 = request("10.0.0.2");
        ipv4.addHeader("X-Forwarded-For", "203.0.113.7");
        MockHttpServletRequest ipv6 = request("2001:db8::1");
        ipv6.addHeader("X-Forwarded-For", "2001:db9::7");

        TrustedProxyClientAddressResolver resolver = resolver("10.0.0.0/8, 10.0.0.0/8, 2001:db8::/32");

        assertEquals("203.0.113.7", resolver.resolve(ipv4));
        assertEquals("2001:db9::7", resolver.resolve(ipv6));
    }

    private static TrustedProxyClientAddressResolver resolver(String trustedProxies) {
        return new TrustedProxyClientAddressResolver(trustedProxies);
    }

    private static MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
