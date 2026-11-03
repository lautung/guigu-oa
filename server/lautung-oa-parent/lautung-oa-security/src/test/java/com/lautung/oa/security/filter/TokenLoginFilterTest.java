package com.lautung.oa.security.filter;

import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.security.custom.CustomUser;
import com.lautung.oa.security.login.InvalidLoginInputException;
import com.lautung.oa.security.login.LoginAttemptLimiter;
import com.lautung.oa.security.login.LoginAudit;
import com.lautung.oa.security.login.LoginEvent;
import com.lautung.oa.security.login.LoginEventType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class TokenLoginFilterTest {

    private static final String SECRET =
            "test-jwt-secret-for-contract-tests-0000000000000000000000000000000000000000";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final LoginAttemptLimiter limiter = mock(LoginAttemptLimiter.class);
    private final LoginAudit audit = mock(LoginAudit.class);
    private final JwtHelper jwtHelper = new JwtHelper(SECRET);

    @Test
    public void aBlockedAccountIsRefusedBeforeThePasswordIsChecked() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(limiter.isBlocked("alice", "127.0.0.1")).thenReturn(true);
        TokenLoginFilter filter = filter(manager);

        assertThrows(LockedException.class,
                () -> filter.attemptAuthentication(loginRequest("alice", "secret"), new MockHttpServletResponse()));
        verifyNoInteractions(manager);
        verify(limiter, never()).recordFailure(any(), any(), any());

        LoginEvent event = onlyAuditedEvent(false);
        assertEquals(LoginEventType.LOCKED, event.type());
        assertEquals("alice", event.username());
    }

    @Test
    public void aBlankUsernameIsRejectedBeforeTheLimiterAndManager() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        TokenLoginFilter filter = filter(manager);

        InvalidLoginInputException failure = assertThrows(InvalidLoginInputException.class,
                () -> filter.attemptAuthentication(
                        loginRequestJson("{\"password\":\"secret\"}"), new MockHttpServletResponse()));

        assertEquals("用户名不能为空", failure.getMessage());
        verifyNoInteractions(manager);
        verifyNoInteractions(limiter);
        verifyNoInteractions(audit);
    }

    @Test
    public void aBlankPasswordIsRejectedWithoutCountingAFailure() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        TokenLoginFilter filter = filter(manager);

        InvalidLoginInputException failure = assertThrows(InvalidLoginInputException.class,
                () -> filter.attemptAuthentication(
                        loginRequestJson("{\"username\":\"alice\"}"), new MockHttpServletResponse()));

        assertEquals("密码不能为空", failure.getMessage());
        verifyNoInteractions(manager);
        verifyNoInteractions(limiter);
        verifyNoInteractions(audit);
    }

    @Test
    public void anUnreadableBodyIsRejectedAsInvalidLoginInput() {
        TokenLoginFilter filter = filter(mock(AuthenticationManager.class));

        InvalidLoginInputException failure = assertThrows(InvalidLoginInputException.class,
                () -> filter.attemptAuthentication(
                        loginRequestJson(""), new MockHttpServletResponse()));

        assertEquals("请输入用户名和密码", failure.getMessage());
        verifyNoInteractions(limiter);
        verifyNoInteractions(audit);
    }

    @Test
    public void anInvalidInputIsReportedWithItsFieldMessage() throws Exception {
        TokenLoginFilter filter = filter(mock(AuthenticationManager.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.unsuccessfulAuthentication(
                loginRequest("alice", "111111"), response, new InvalidLoginInputException("密码不能为空"));

        assertEquals(401, response.getStatus());
        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertEquals(208, body.get("code").asInt());
        assertEquals("密码不能为空", body.get("message").asString());
    }

    @Test
    public void aWrongPasswordIsCounted() {
        AuthenticationManager manager = authentication -> {
            throw new BadCredentialsException("bad credentials");
        };
        TokenLoginFilter filter = filter(manager);

        assertThrows(BadCredentialsException.class,
                () -> filter.attemptAuthentication(loginRequest("alice", "secret"), new MockHttpServletResponse()));
        verify(limiter).recordFailure("alice", "127.0.0.1", USER_AGENT);
        verifyNoInteractions(audit);
    }

    @Test
    public void aLookupFailureIsNotCountedAsAGuessedPassword() {
        AuthenticationManager manager = authentication -> {
            throw new InternalAuthenticationServiceException("database is down");
        };
        TokenLoginFilter filter = filter(manager);

        assertThrows(InternalAuthenticationServiceException.class,
                () -> filter.attemptAuthentication(loginRequest("alice", "secret"), new MockHttpServletResponse()));
        verify(limiter, never()).recordFailure(any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    public void aSuccessfulPasswordClearsTheAccountFailuresAndMintsBothTokens() throws Exception {
        CustomUser principal = user("alice", 1, 7L);
        AuthenticationManager manager = authentication ->
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        TokenLoginFilter filter = filter(manager);

        Authentication authentication = filter.attemptAuthentication(
                loginRequest("alice", "111111"), new MockHttpServletResponse());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.successfulAuthentication(loginRequest("alice", "111111"), response, null, authentication);

        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertEquals(200, body.get("code").asInt());
        String token = body.path("data").path("token").asString("");
        String refreshToken = body.path("data").path("refreshToken").asString("");
        assertFalse(token.isEmpty());
        assertFalse(refreshToken.isEmpty());
        assertEquals(7L, jwtHelper.readAccessToken(token).tokenVersion());
        assertEquals(7L, jwtHelper.readRefreshToken(refreshToken).tokenVersion());
        verify(limiter).recordSuccess("alice", "127.0.0.1");

        LoginEvent event = onlyAuditedEvent(true);
        assertEquals(LoginEventType.SUCCESS, event.type());
        assertEquals("alice", event.username());
        assertEquals("127.0.0.1", event.sourceAddress());
        assertEquals(USER_AGENT, event.userAgent(), "the event must carry the request user-agent");
    }

    @Test
    public void aCorrectPasswordThatArrivesAfterAConcurrentLockGetsNoTokenAndIsLogOnly() throws Exception {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user("alice", 1), null, List.of());
        when(limiter.isBlocked("alice", "127.0.0.1")).thenReturn(true);
        TokenLoginFilter filter = filter(mock(AuthenticationManager.class));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.successfulAuthentication(loginRequest("alice", "111111"), response, null, authentication);

        assertEquals(401, response.getStatus());
        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertEquals(208, body.get("code").asInt());
        assertTrue(body.get("message").asString().contains("频繁"));
        assertTrue(body.path("data").path("token").asString("").isEmpty());
        verify(limiter, never()).recordSuccess(any(), any());

        LoginEvent event = onlyAuditedEvent(false);
        assertEquals(LoginEventType.LOCKED, event.type());
        assertEquals("登录尝试过于频繁，未签发令牌", event.msg());
    }

    @Test
    public void aDisabledAccountIsRefusedWithoutClearingItsFailuresButIsAudited() throws Exception {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user("alice", 0), null, List.of());
        TokenLoginFilter filter = filter(mock(AuthenticationManager.class));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.successfulAuthentication(loginRequest("alice", "111111"), response, null, authentication);

        assertEquals(401, response.getStatus());
        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertEquals(209, body.get("code").asInt());
        assertEquals("账号已停用", body.get("message").asString());
        verify(limiter, never()).recordSuccess(any(), any());

        LoginEvent event = onlyAuditedEvent(true);
        assertEquals(LoginEventType.FAILURE, event.type());
        assertEquals("账号已停用", event.msg());
    }

    @Test
    public void aLockedExceptionIsReportedWithTheTooFrequentMessage() throws Exception {
        TokenLoginFilter filter = filter(mock(AuthenticationManager.class));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.unsuccessfulAuthentication(
                loginRequest("alice", "111111"), response, new LockedException("too many attempts"));

        assertEquals(401, response.getStatus());
        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertEquals(208, body.get("code").asInt());
        assertTrue(body.get("message").asString().contains("频繁"));
        assertTrue(body.path("data").path("token").asString("").isEmpty());
    }

    private TokenLoginFilter filter(AuthenticationManager manager) {
        return new TokenLoginFilter(manager, jwtHelper, limiter, VALIDATOR, audit);
    }

    /** Exactly one event through the durable or the log-only path, never both. */
    private LoginEvent onlyAuditedEvent(boolean durable) {
        ArgumentCaptor<LoginEvent> durableEvent = ArgumentCaptor.forClass(LoginEvent.class);
        if (durable) {
            verify(audit).record(durableEvent.capture());
            verify(audit, never()).recordLogOnly(any());
        } else {
            verify(audit).recordLogOnly(durableEvent.capture());
            verify(audit, never()).record(any());
        }
        return durableEvent.getValue();
    }

    private static CustomUser user(String username, int status) {
        return user(username, status, 0L);
    }

    private static CustomUser user(String username, int status, long tokenVersion) {
        SysUser sysUser = new SysUser();
        sysUser.setId(1L);
        sysUser.setUsername(username);
        sysUser.setStatus(status);
        sysUser.setTokenVersion(tokenVersion);
        return new CustomUser(sysUser, List.of());
    }

    private static MockHttpServletRequest loginRequest(String username, String password) {
        return loginRequestJson("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpServletRequest loginRequestJson(String json) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/system/index/login");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        request.addHeader("User-Agent", USER_AGENT);
        return request;
    }
}
