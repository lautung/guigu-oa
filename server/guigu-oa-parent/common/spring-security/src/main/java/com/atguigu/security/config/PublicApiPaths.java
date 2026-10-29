package com.atguigu.security.config;

public final class PublicApiPaths {

    private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    private static final String ACTUATOR_HEALTH_PREFIX = ACTUATOR_HEALTH_PATH + "/";

    public static final String[] REQUEST_MATCHERS = {
            "/admin/system/index/logout",
            "/admin/system/index/refresh",
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
        if ("/admin/system/index/login".equals(uri)) {
            return true;
        }
        return "/admin/system/index/refresh".equals(uri)
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
