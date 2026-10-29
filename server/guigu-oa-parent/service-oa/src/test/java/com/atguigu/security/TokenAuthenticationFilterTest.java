package com.atguigu.security;

import com.atguigu.common.jwt.JwtHelper;
import com.atguigu.model.system.SysUser;
import com.atguigu.security.custom.CustomUser;
import com.atguigu.security.custom.UserDetailsService;
import com.atguigu.security.filter.TokenAuthenticationFilter;
import com.atguigu.security.revocation.TokenRevocation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenAuthenticationFilterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @AfterEach
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void missingTokenOnProtectedApiReturnsLoginError() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/admin/system/index/info", null, chain);

        assertLoginError(response);
        assertEquals(0, chain.calls);
    }

    @Test
    public void swaggerUiIsReachableWithoutToken() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/swagger-ui/index.html", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void swaggerUiHtmlIsReachableWithoutToken() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/swagger-ui.html", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void openApiDocsAreReachableWithoutToken() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/v3/api-docs", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void openApiDocsSubpathIsReachableWithoutToken() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/v3/api-docs/swagger-config", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void loginEndpointSkipsTokenCheck() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/admin/system/index/login", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void refreshEndpointSkipsAccessTokenCheck() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/admin/system/index/refresh", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void logoutEndpointSkipsTokenCheckSoAnIdleClientCanTurnItsCredentialsIn() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/admin/system/index/logout", "expired-access", chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void anAccessTokenFromAnEarlierTokenVersionIsRejected() throws Exception {
        JwtHelper jwtHelper = new JwtHelper(
                "test-jwt-secret-for-contract-tests-0000000000000000000000000000000000000000");
        String token = jwtHelper.createTokenPair(1L, "admin", 3L).token();
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setStatus(1);
        user.setTokenVersion(4L);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("admin"))
                .thenReturn(new CustomUser(user, List.of()));
        TokenRevocation revocation = mock(TokenRevocation.class);
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/system/index/info");
        request.setRequestURI("/admin/system/index/info");
        request.addHeader("token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TokenAuthenticationFilter(null, jwtHelper, userDetailsService, revocation)
                .doFilter(request, response, chain);

        assertLoginError(response);
        assertEquals(0, chain.calls);
    }

    private MockHttpServletResponse filter(String uri, String token, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (token != null) {
            request.addHeader("token", token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TokenAuthenticationFilter(null, null, null, null).doFilter(request, response, chain);
        return response;
    }

    private void assertLoginError(MockHttpServletResponse response) throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertEquals(208, body.get("code").asInt());
        assertTrue(body.get("message").asString().contains("认证失败"));
    }

    private static final class RecordingFilterChain implements FilterChain {
        private int calls;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            calls++;
        }
    }
}
