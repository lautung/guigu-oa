package com.lautung.oa.security.address;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 客户端来源地址的默认解析（#262，ADR 0063）：socket 对端不在配置的受信代理网段内时，
 * {@code X-Real-IP} 与 {@code X-Forwarded-For} 一律忽略——直连后端无法靠换头制造新来源。
 *
 * <p>对端受信时，从 {@code X-Forwarded-For} 右向左跳过受信代理，取第一个非受信跳；链为空或
 * 全是受信地址时才看 {@code X-Real-IP}，最后回落 socket 对端。右侧优先保证 nginx 追加的真实
 * 对端永远压过客户端预置的假跳，未配置受信代理（默认）时连这个假设都不需要。
 *
 * <p>地址字面量只收合法 IPv4/IPv6；空白、超长、非 IP、非受信链里的坏跳都跳过，不制造假来源。
 * 非法 CIDR 只 WARN 并从受信集合里剔除，配置错误不会静默扩大信任面，也不阻断启动。
 */
public final class TrustedProxyClientAddressResolver implements ClientAddressResolver {

    private static final Logger log = LoggerFactory.getLogger(TrustedProxyClientAddressResolver.class);

    private static final String REAL_IP = "X-Real-IP";
    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";
    private static final int MAX_ADDRESS_LENGTH = 45;

    private final List<IpAddressMatcher> trustedProxies;

    /**
     * @param trustedProxyCidrs 逗号分隔的 CIDR 列表（IPv4/IPv6 均可）；空值表示不信任任何转发头
     */
    public TrustedProxyClientAddressResolver(String trustedProxyCidrs) {
        this.trustedProxies = parse(trustedProxyCidrs);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!StringUtils.hasText(remoteAddress)) {
            return UNKNOWN;
        }
        String peer = remoteAddress.trim();
        if (trustedProxies.isEmpty() || !isAddressLiteral(peer) || !isTrusted(peer)) {
            return peer;
        }
        String forwarded = firstNonTrustedHop(request.getHeaders(FORWARDED_FOR));
        if (forwarded != null) {
            return forwarded;
        }
        String realIp = normalize(request.getHeader(REAL_IP));
        return realIp != null ? realIp : peer;
    }

    /** 右侧（最近）优先：收到的每一行按出现顺序拼接，最后一跳是离我们最近的代理。 */
    private String firstNonTrustedHop(Enumeration<String> headerValues) {
        if (headerValues == null) {
            return null;
        }
        List<String> hops = new ArrayList<>();
        while (headerValues.hasMoreElements()) {
            String value = headerValues.nextElement();
            if (value != null && !value.isEmpty()) {
                for (String hop : value.split(",")) {
                    hops.add(hop);
                }
            }
        }
        for (int index = hops.size() - 1; index >= 0; index -= 1) {
            String hop = normalize(hops.get(index));
            if (hop != null && !isTrusted(hop)) {
                return hop;
            }
        }
        return null;
    }

    private boolean isTrusted(String address) {
        for (IpAddressMatcher matcher : trustedProxies) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 归一成可返回的地址字面量：去空白、去 IPv6 方括号、去端口；不合法返回 {@code null}。
     */
    private static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("[")) {
            int closing = value.indexOf(']');
            if (closing < 0) {
                return null;
            }
            String remainder = value.substring(closing + 1);
            if (!remainder.isEmpty()
                    && !(remainder.startsWith(":") && isDigits(remainder.substring(1)))) {
                return null;
            }
            String bracketed = value.substring(1, closing);
            return isAddressLiteral(bracketed) ? bracketed : null;
        }
        int colon = value.indexOf(':');
        if (colon >= 0 && colon == value.lastIndexOf(':')) {
            // 只有一个冒号：IPv4:port；拆出的 host 必须本身是 IPv4，避免把 IPv6 误当端口。
            String host = value.substring(0, colon);
            String port = value.substring(colon + 1);
            if (isDigits(port) && isIpv4Literal(host)) {
                return host;
            }
        }
        return isAddressLiteral(value) ? value : null;
    }

    private static boolean isAddressLiteral(String value) {
        return isIpv4Literal(value) || isIpv6Literal(value);
    }

    /**
     * 纯语法校验，不交给 {@code InetAddress.getByName}：后者对不像 IP 的输入会发起 DNS
     * 解析（本机 TUN DNS 甚至把 {@code 999.1.1.1} 解析成地址），请求头里的值绝不能触发。
     */
    private static boolean isIpv4Literal(String value) {
        if (value == null) {
            return false;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !isDigits(part)
                    || Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(String value) {
        if (value == null || value.length() > MAX_ADDRESS_LENGTH || value.indexOf(':') < 0) {
            return false;
        }
        String address = value;
        int embeddedIpv4Groups = 0;
        int lastColon = address.lastIndexOf(':');
        if (address.substring(lastColon + 1).indexOf('.') >= 0) {
            if (!isIpv4Literal(address.substring(lastColon + 1))) {
                return false;
            }
            embeddedIpv4Groups = 2;
            address = address.substring(0, lastColon);
        }
        int compression = address.indexOf("::");
        if (compression != address.lastIndexOf("::")) {
            return false;
        }
        String[] groups;
        if (compression >= 0) {
            String left = address.substring(0, compression);
            String right = address.substring(compression + 2);
            String[] leftGroups = left.isEmpty() ? new String[0] : left.split(":", -1);
            String[] rightGroups = right.isEmpty() ? new String[0] : right.split(":", -1);
            groups = new String[leftGroups.length + rightGroups.length];
            System.arraycopy(leftGroups, 0, groups, 0, leftGroups.length);
            System.arraycopy(rightGroups, 0, groups, leftGroups.length, rightGroups.length);
        } else {
            groups = address.split(":", -1);
        }
        for (String group : groups) {
            if (!isHexGroup(group)) {
                return false;
            }
        }
        int present = groups.length + embeddedIpv4Groups;
        return compression >= 0 ? present < 8 : present == 8;
    }

    private static boolean isHexGroup(String group) {
        if (group.isEmpty() || group.length() > 4) {
            return false;
        }
        for (int index = 0; index < group.length(); index += 1) {
            if (Character.digit(group.charAt(index), 16) == -1) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidCidr(String value) {
        int slash = value.indexOf('/');
        if (slash < 0) {
            return isAddressLiteral(value);
        }
        if (value.indexOf('/', slash + 1) >= 0) {
            return false;
        }
        String address = value.substring(0, slash);
        String prefix = value.substring(slash + 1);
        if (prefix.isEmpty() || prefix.length() > 3 || !isDigits(prefix)) {
            return false;
        }
        int bits = Integer.parseInt(prefix);
        if (isIpv4Literal(address)) {
            return bits <= 32;
        }
        return isIpv6Literal(address) && bits <= 128;
    }

    private static boolean isDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index += 1) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static List<IpAddressMatcher> parse(String trustedProxyCidrs) {
        if (!StringUtils.hasText(trustedProxyCidrs)) {
            return List.of();
        }
        List<IpAddressMatcher> matchers = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (String entry : trustedProxyCidrs.split(",")) {
            String cidr = entry.trim();
            if (cidr.isEmpty()) {
                continue;
            }
            if (!isValidCidr(cidr)) {
                rejected.add(cidr);
                continue;
            }
            matchers.add(new IpAddressMatcher(cidr));
        }
        if (!rejected.isEmpty()) {
            log.warn("Ignoring invalid trusted proxy entries {}; they are never treated as proxies",
                    rejected);
        }
        return List.copyOf(matchers);
    }
}
