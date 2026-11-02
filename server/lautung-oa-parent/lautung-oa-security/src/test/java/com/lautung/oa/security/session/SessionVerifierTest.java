package com.lautung.oa.security.session;

import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.security.custom.CustomUser;
import com.lautung.oa.security.custom.UserDetailsService;
import com.lautung.oa.security.response.AuthenticationFailure;
import com.lautung.oa.security.revocation.TokenRevocation;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 会话核验 module 的分支与顺序契约（#173）：每条拒绝原因一个用例；
 * 「吊销先于加载用户」「版本先于停用」是冻结顺序，由调用方不参与的红线钉住。
 */
public class SessionVerifierTest {

    private static final String SECRET =
            "test-jwt-secret-for-contract-tests-0000000000000000000000000000000000000000";

    private final JwtHelper jwtHelper = mock(JwtHelper.class);
    private final TokenRevocation revocation = mock(TokenRevocation.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final SessionVerifier verifier = new SessionVerifier(jwtHelper, revocation, userDetailsService);

    @Test
    public void anUnparsableCredentialIsInvalidWithoutTouchingCollaborators() {
        when(jwtHelper.readAccessToken("garbage")).thenReturn(null);

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.ACCESS, "garbage");

        assertRejected(outcome, AuthenticationFailure.INVALID_TOKEN);
        verifyNoInteractions(revocation, userDetailsService);
    }

    @Test
    public void aRevokedCredentialIsInvalidBeforeTheUserIsLoaded() {
        when(jwtHelper.readAccessToken("t")).thenReturn(claims(1L, "alice", "sid-1", 7L));
        when(revocation.isRevoked("sid-1")).thenReturn(true);

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.ACCESS, "t");

        assertRejected(outcome, AuthenticationFailure.INVALID_TOKEN);
        verify(userDetailsService, never()).loadUserByUsername("alice");
    }

    @Test
    public void refreshCredentialsAreReadWithTheRefreshReader() {
        when(jwtHelper.readRefreshToken("rt")).thenReturn(claims(1L, "alice", "sid-1", 7L));
        givenLoadedUser(user(1L, "alice", 1, 7L));

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.REFRESH, "rt");

        assertInstanceOf(SessionVerifier.Outcome.Recognized.class, outcome);
        verify(jwtHelper).readRefreshToken("rt");
        verify(jwtHelper, never()).readAccessToken("rt");
    }

    @Test
    public void aBlankUsernameIsInvalidWithoutLoadingAUser() {
        when(jwtHelper.readAccessToken("t")).thenReturn(claims(1L, "", "sid-1", 7L));

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.ACCESS, "t");

        assertRejected(outcome, AuthenticationFailure.INVALID_TOKEN);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    public void aMissingUserIdIsInvalidWithoutLoadingAUser() {
        when(jwtHelper.readAccessToken("t")).thenReturn(claims(null, "alice", "sid-1", 7L));

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.ACCESS, "t");

        assertRejected(outcome, AuthenticationFailure.INVALID_TOKEN);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    public void anUnknownUsernameIsInvalid() {
        when(jwtHelper.readAccessToken("t")).thenReturn(claims(1L, "ghost", "sid-1", 7L));
        when(userDetailsService.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("gone"));

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.ACCESS, "t");

        assertRejected(outcome, AuthenticationFailure.INVALID_TOKEN);
    }

    @Test
    public void aUserIdThatDoesNotMatchTheLoadedUserIsInvalid() {
        when(jwtHelper.readAccessToken("t")).thenReturn(claims(1L, "alice", "sid-1", 7L));
        givenLoadedUser(user(2L, "alice", 1, 7L));

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.ACCESS, "t");

        assertRejected(outcome, AuthenticationFailure.INVALID_TOKEN);
    }

    @Test
    public void aTokenFromAnEarlierPasswordVersionIsInvalid() {
        when(jwtHelper.readAccessToken("t")).thenReturn(claims(1L, "alice", "sid-1", 3L));
        givenLoadedUser(user(1L, "alice", 1, 4L));

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.ACCESS, "t");

        assertRejected(outcome, AuthenticationFailure.INVALID_TOKEN);
    }

    @Test
    public void aStaleVersionIsRejectedAsInvalidEvenWhenTheAccountIsDisabled() {
        // 冻结顺序：版本判定先于停用判定——旧版本 + 停用得到 208，不是 209。
        when(jwtHelper.readAccessToken("t")).thenReturn(claims(1L, "alice", "sid-1", 3L));
        givenLoadedUser(user(1L, "alice", 0, 4L));

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.ACCESS, "t");

        assertRejected(outcome, AuthenticationFailure.INVALID_TOKEN);
    }

    @Test
    public void aDisabledUserIsRejectedAsDisabled() {
        when(jwtHelper.readAccessToken("t")).thenReturn(claims(1L, "alice", "sid-1", 7L));
        givenLoadedUser(user(1L, "alice", 0, 7L));

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.ACCESS, "t");

        assertRejected(outcome, AuthenticationFailure.ACCOUNT_DISABLED);
    }

    @Test
    public void aValidAccessTokenRecognizesItsUser() {
        JwtHelper.TokenClaims claims = claims(1L, "alice", "sid-1", 7L);
        SysUser user = user(1L, "alice", 1, 7L);
        when(jwtHelper.readAccessToken("t")).thenReturn(claims);
        when(revocation.isRevoked("sid-1")).thenReturn(false);
        givenLoadedUser(user);

        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.ACCESS, "t");

        SessionVerifier.Outcome.Recognized recognized =
                assertInstanceOf(SessionVerifier.Outcome.Recognized.class, outcome);
        assertSame(claims, recognized.claims());
        assertSame(user, recognized.user());
        verify(revocation).isRevoked("sid-1");
    }

    @Test
    public void aRealRefreshTokenRoundTripsThroughTheRealParser() {
        SessionVerifier realVerifier = realParserVerifier();
        String refreshToken = refreshTokenForAlice();
        when(revocation.isRevoked("sid-1")).thenReturn(false);
        givenLoadedUser(user(1L, "alice", 1, 7L));

        SessionVerifier.Outcome outcome = realVerifier.verify(SessionVerifier.TokenKind.REFRESH, refreshToken);

        SessionVerifier.Outcome.Recognized recognized =
                assertInstanceOf(SessionVerifier.Outcome.Recognized.class, outcome);
        assertEquals(1L, recognized.claims().userId());
        assertEquals("alice", recognized.claims().username());
        assertEquals(7L, recognized.claims().tokenVersion());
        assertFalse(recognized.claims().sessionId().isBlank());
    }

    @Test
    public void theSameCredentialPresentedAsTheWrongKindIsRejected() {
        SessionVerifier realVerifier = realParserVerifier();

        SessionVerifier.Outcome outcome = realVerifier.verify(SessionVerifier.TokenKind.ACCESS, refreshTokenForAlice());

        assertRejected(outcome, AuthenticationFailure.INVALID_TOKEN);
        verifyNoInteractions(revocation, userDetailsService);
    }

    @Test
    public void aBlankCredentialIsInvalidWithoutTouchingCollaborators() {
        SessionVerifier.Outcome outcome = verifier.verify(SessionVerifier.TokenKind.REFRESH, "  ");

        assertRejected(outcome, AuthenticationFailure.INVALID_TOKEN);
        verifyNoInteractions(revocation, userDetailsService);
    }

    /** 真实解析器路线：真实 JwtHelper 现签现验，吊销与用户加载仍是 mock。 */
    private SessionVerifier realParserVerifier() {
        return new SessionVerifier(new JwtHelper(SECRET), revocation, userDetailsService);
    }

    private static String refreshTokenForAlice() {
        return new JwtHelper(SECRET).createTokenPair(1L, "alice", 7L).refreshToken();
    }

    private static JwtHelper.TokenClaims claims(Long userId, String username, String sessionId, long tokenVersion) {
        return new JwtHelper.TokenClaims(userId, username, sessionId, tokenVersion, new Date());
    }

    private static SysUser user(Long id, String username, int status, long tokenVersion) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(status);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    private void givenLoadedUser(SysUser user) {
        when(userDetailsService.loadUserByUsername(user.getUsername()))
                .thenReturn(new CustomUser(user, List.of()));
    }

    private static void assertRejected(SessionVerifier.Outcome outcome, AuthenticationFailure expected) {
        SessionVerifier.Outcome.Rejected rejected =
                assertInstanceOf(SessionVerifier.Outcome.Rejected.class, outcome);
        assertEquals(expected, rejected.failure());
    }
}
