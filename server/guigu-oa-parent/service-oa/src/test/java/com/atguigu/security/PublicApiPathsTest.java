package com.atguigu.security;

import com.atguigu.security.config.PublicApiPaths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicApiPathsTest {

    @Test
    void rejectsNullAndEmptyUris() {
        assertFalse(PublicApiPaths.permitsAnonymous(null));
        assertFalse(PublicApiPaths.permitsAnonymous(""));
    }

    @Test
    void permitsEveryAnonymousEndpoint() {
        assertTrue(PublicApiPaths.permitsAnonymous("/admin/system/index/login"));
        assertTrue(PublicApiPaths.permitsAnonymous("/admin/system/index/refresh"));
        assertTrue(PublicApiPaths.permitsAnonymous("/v3/api-docs"));
        assertTrue(PublicApiPaths.permitsAnonymous("/v3/api-docs/swagger-config"));
        assertTrue(PublicApiPaths.permitsAnonymous("/actuator/health"));
        assertTrue(PublicApiPaths.permitsAnonymous("/swagger-ui.html"));
        assertTrue(PublicApiPaths.permitsAnonymous("/swagger-ui/index.html"));
        assertTrue(PublicApiPaths.permitsAnonymous("/favicon.ico"));
        assertTrue(PublicApiPaths.permitsAnonymous("/webjars/swagger-ui.js"));
        assertTrue(PublicApiPaths.permitsAnonymous("/admin/processImage/1"));
    }

    @Test
    void permitsEveryActuatorHealthGroupWithoutOpeningTheRestOfActuator() {
        assertTrue(PublicApiPaths.permitsAnonymous("/actuator/health/readiness"));
        assertTrue(PublicApiPaths.permitsAnonymous("/actuator/health/liveness"));
        assertTrue(PublicApiPaths.permitsAnonymous("/actuator/health/startup"));
        assertFalse(PublicApiPaths.permitsAnonymous("/actuator"));
        assertFalse(PublicApiPaths.permitsAnonymous("/actuator/healthz"));
        assertFalse(PublicApiPaths.permitsAnonymous("/actuator/env"));
        assertFalse(PublicApiPaths.permitsAnonymous("/actuator/health/../env"));
    }

    @Test
    void keepsBusinessEndpointsBehindAuthentication() {
        assertFalse(PublicApiPaths.permitsAnonymous("/admin/system/sysUser/1/10"));
        assertFalse(PublicApiPaths.permitsAnonymous("/admin/process/findPending/1/10"));
        assertFalse(PublicApiPaths.permitsAnonymous("/swagger-ui"));
        assertFalse(PublicApiPaths.permitsAnonymous("/v3/api-docs-other"));
    }
}
