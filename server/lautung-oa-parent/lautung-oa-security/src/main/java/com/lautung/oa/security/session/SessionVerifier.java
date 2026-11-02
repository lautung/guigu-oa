package com.lautung.oa.security.session;

import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.security.custom.AccountStatus;
import com.lautung.oa.security.custom.CustomUser;
import com.lautung.oa.security.custom.UserDetailsService;
import com.lautung.oa.security.response.AuthenticationFailure;
import com.lautung.oa.security.revocation.TokenRevocation;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 会话核验（#173，GLOSSARY「会话核验」）：过滤器与续期端点共用的唯一凭证判定。
 * 判定顺序冻结：解析 → 吊销 → 良构 → 按用户名加载 → 身份比对 → token_version → 停用；
 * ADR 0022/0029/0037 的语义与 server-modules.md 的六层拒绝顺序都建立在它上面。
 * 核验之后的动作（构建 Authentication、签发新令牌）归调用方，不在这里。
 */
public class SessionVerifier {

    public enum TokenKind { ACCESS, REFRESH }

    public sealed interface Outcome {
        record Recognized(JwtHelper.TokenClaims claims, SysUser user) implements Outcome {}

        record Rejected(AuthenticationFailure failure) implements Outcome {}
    }

    private final JwtHelper jwtHelper;
    private final TokenRevocation tokenRevocation;
    private final UserDetailsService userDetailsService;

    public SessionVerifier(
            JwtHelper jwtHelper,
            TokenRevocation tokenRevocation,
            UserDetailsService userDetailsService) {
        this.jwtHelper = jwtHelper;
        this.tokenRevocation = tokenRevocation;
        this.userDetailsService = userDetailsService;
    }

    public Outcome verify(TokenKind kind, String credential) {
        JwtHelper.TokenClaims claims = kind == TokenKind.REFRESH
                ? jwtHelper.readRefreshToken(credential)
                : jwtHelper.readAccessToken(credential);
        if (claims == null || tokenRevocation.isRevoked(claims.sessionId())) {
            return new Outcome.Rejected(AuthenticationFailure.INVALID_TOKEN);
        }
        String username = claims.username();
        Long userId = claims.userId();
        if (!StringUtils.hasText(username) || userId == null) {
            return new Outcome.Rejected(AuthenticationFailure.INVALID_TOKEN);
        }

        SysUser user;
        try {
            user = ((CustomUser) userDetailsService.loadUserByUsername(username)).getSysUser();
        } catch (UsernameNotFoundException e) {
            return new Outcome.Rejected(AuthenticationFailure.INVALID_TOKEN);
        }
        if (!Objects.equals(userId, user.getId())) {
            return new Outcome.Rejected(AuthenticationFailure.INVALID_TOKEN);
        }
        // A password change moves the row's token version, so every earlier token stops here (ADR 0029).
        if (!user.acceptsTokenVersion(claims.tokenVersion())) {
            return new Outcome.Rejected(AuthenticationFailure.INVALID_TOKEN);
        }
        if (!AccountStatus.isEnabled(user.getStatus())) {
            return new Outcome.Rejected(AuthenticationFailure.ACCOUNT_DISABLED);
        }
        return new Outcome.Recognized(claims, user);
    }
}
