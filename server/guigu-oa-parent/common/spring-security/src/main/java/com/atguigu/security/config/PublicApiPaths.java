package com.atguigu.security.config;

public final class PublicApiPaths {

    public static final String[] REQUEST_MATCHERS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/favicon.ico",
            "/webjars/**",
            "/admin/processImage/**"
    };

    private PublicApiPaths() {
    }

    public static boolean permitsAnonymous(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        if ("/admin/system/index/login".equals(uri)) {
            return true;
        }
        return "/v3/api-docs".equals(uri)
                || uri.startsWith("/v3/api-docs/")
                || "/swagger-ui.html".equals(uri)
                || uri.startsWith("/swagger-ui/")
                || "/favicon.ico".equals(uri)
                || uri.startsWith("/webjars/")
                || uri.startsWith("/admin/processImage/");
    }
}
