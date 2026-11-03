package com.lautung.oa.security;

import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.security.filter.TokenAuthenticationFilter;
import com.lautung.oa.security.response.AuthenticationFailure;
import com.lautung.oa.security.session.OnlineSessionRegistry;
import com.lautung.oa.security.session.SessionVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class TokenAuthenticationFilterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SessionVerifier sessionVerifier = mock(SessionVerifier.class);
    private final OnlineSessionRegistry onlineSessionRegistry = mock(OnlineSessionRegistry.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    @AfterEach
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void missingTokenOnProtectedApiReturnsLoginError() throws Exception {
        rejected(AuthenticationFailure.INVALID_TOKEN);
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
        // Anonymous paths must not touch the verifier at all (#173).
        verifyNoInteractions(sessionVerifier);
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
    public void aRejectedVerdictIsWrittenAsLoginError() throws Exception {
        rejected(AuthenticationFailure.INVALID_TOKEN);
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/admin/system/index/info", "stale-token", chain);

        assertLoginError(response);
        assertEquals(0, chain.calls);
    }

    @Test
    public void aDisabledVerdictIsWrittenAsAccountDisabled() throws Exception {
        rejected(AuthenticationFailure.ACCOUNT_DISABLED);
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/admin/system/index/info", "any-token", chain);

        assertEquals(401, response.getStatus());
        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertEquals(209, body.get("code").asInt());
        assertTrue(body.get("message").asString().contains("停用"));
        assertEquals(0, chain.calls);
    }

    @Test
    public void aRecognizedVerdictBuildsAuthenticationAndContinuesTheChain() throws Exception {
        JwtHelper.TokenClaims claims = new JwtHelper.TokenClaims(1L, "alice", "sid-1", 7L, new Date());
        when(sessionVerifier.verify(any(), any()))
                .thenReturn(new SessionVerifier.Outcome.Recognized(claims, user()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/admin/system/index/info", "valid-token", chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
        UsernamePasswordAuthenticationToken authentication = assertInstanceOf(
                UsernamePasswordAuthenticationToken.class,
                SecurityContextHolder.getContext().getAuthentication());
        assertEquals("alice", authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().isEmpty());
        // 认出之后按会话节流刷新最后活跃（ADR 0060），一次请求一次判定。
        verify(onlineSessionRegistry).touch("sid-1");
    }

    @Test
    public void aRecognizedVerdictExposesTheSessionIdOnlyWhileTheChainRuns() throws Exception {
        JwtHelper.TokenClaims claims = new JwtHelper.TokenClaims(1L, "alice", "sid-1", 7L, new Date());
        when(sessionVerifier.verify(any(), any()))
                .thenReturn(new SessionVerifier.Outcome.Recognized(claims, user()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        MockHttpServletResponse response = filter("/admin/system/index/info", "valid-token",
                (request, result) -> assertEquals("sid-1", LoginUserInfoHelper.getSessionId()));

        assertEquals("", response.getContentAsString());
        // 请求级 ThreadLocal 在 finally 里清掉，不残留到同线程的下一请求。
        assertNull(LoginUserInfoHelper.getSessionId());
        assertNull(LoginUserInfoHelper.getUserId());
        assertNull(LoginUserInfoHelper.getUsername());
    }

    @Test
    public void aRejectedVerdictDoesNotTouchTheSessionRegistry() throws Exception {
        rejected(AuthenticationFailure.INVALID_TOKEN);
        MockHttpServletResponse response = filter("/admin/system/index/info", "stale-token",
                (request, result) -> { });

        assertLoginError(response);
        verifyNoInteractions(onlineSessionRegistry);
    }

    private void rejected(AuthenticationFailure failure) {
        when(sessionVerifier.verify(any(), any()))
                .thenReturn(new SessionVerifier.Outcome.Rejected(failure));
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("alice");
        user.setStatus(1);
        user.setTokenVersion(7L);
        return user;
    }

    private MockHttpServletResponse filter(String uri, String token, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (token != null) {
            request.addHeader("token", token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TokenAuthenticationFilter(sessionVerifier, redisTemplate, onlineSessionRegistry)
                .doFilter(request, response, chain);
        return response;
    }

    private void assertLoginError(MockHttpServletResponse response) throws Exception {
        assertEquals(401, response.getStatus());
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
