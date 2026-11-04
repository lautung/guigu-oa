package com.lautung.oa.security.config;

public final class PublicApiPaths {

    private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    private static final String ACTUATOR_HEALTH_PREFIX = ACTUATOR_HEALTH_PATH + "/";
    private static final String LOGIN_PATH = "/admin/system/index/login";
    private static final String REGISTER_PATH = "/admin/system/index/register";
    private static final String REGISTER_SWITCH_PATH = REGISTER_PATH + "/switch";

    public static final String[] REQUEST_MATCHERS = {
            "/admin/system/index/logout",
            "/admin/system/index/refresh",
            REGISTER_PATH,
            REGISTER_SWITCH_PATH,
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            ACTUATOR_HEALTH_PATH,
            ACTUATOR_HEALTH_PREFIX + "**",
            "/favicon.ico",
            "/webjars/**",
            "/admin/processImage/**"
    };

    private PublicApiPaths() {
    }

    public static boolean permitsAnonymous(String uri) {
        if (uri == null || uri.isEmpty() || climbsDirectories(uri)) {
            return false;
        }
        if (LOGIN_PATH.equals(uri)) {
            return true;
        }
        return REGISTER_PATH.equals(uri)
                || REGISTER_SWITCH_PATH.equals(uri)
                || "/admin/system/index/refresh".equals(uri)
                || "/admin/system/index/logout".equals(uri)
                || "/v3/api-docs".equals(uri)
                || uri.startsWith("/v3/api-docs/")
                || ACTUATOR_HEALTH_PATH.equals(uri)
                || uri.startsWith(ACTUATOR_HEALTH_PREFIX)
                || "/swagger-ui.html".equals(uri)
                || uri.startsWith("/swagger-ui/")
                || "/favicon.ico".equals(uri)
                || uri.startsWith("/webjars/")
                || uri.startsWith("/admin/processImage/");
    }

    /**
     * A whitelist match must never survive a parent-directory segment: the prefix checks below
     * are literal, so `/actuator/health/../env` would otherwise inherit the health exemption.
     */
    private static boolean climbsDirectories(String uri) {
        return uri.contains("/../") || uri.endsWith("/..");
    }
}
