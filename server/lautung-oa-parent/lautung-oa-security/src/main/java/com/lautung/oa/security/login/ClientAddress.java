package com.lautung.oa.security.login;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Resolves the client address for failure counting. The demo reaches the API through the client
 * container's nginx, which overwrites {@code X-Real-IP} and appends its own peer to
 * {@code X-Forwarded-For}; trusting the nearest hop means a client cannot prepend a value to
 * dodge the source counter. Anything that is not an address literal falls back to the socket peer.
 */
public final class ClientAddress {

    private static final String REAL_IP = "X-Real-IP";
    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern ADDRESS_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        String realIp = request.getHeader(REAL_IP);
        if (isAddressLiteral(realIp)) {
            return realIp.trim();
        }
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (StringUtils.hasText(forwarded)) {
            String[] hops = forwarded.split(",");
            String nearestHop = hops[hops.length - 1].trim();
            if (isAddressLiteral(nearestHop)) {
                return nearestHop;
            }
        }
        String remoteAddress = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddress) ? remoteAddress : "unknown";
    }

    private static boolean isAddressLiteral(String value) {
        return StringUtils.hasText(value) && ADDRESS_LITERAL.matcher(value.trim()).matches();
    }
}
