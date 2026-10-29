package com.atguigu.security.filter;

import com.atguigu.common.jwt.JwtHelper;
import com.atguigu.common.result.ResponseUtil;
import com.atguigu.common.result.Result;
import com.atguigu.common.result.ResultCodeEnum;
import com.atguigu.security.custom.AccountStatus;
import com.atguigu.security.custom.CustomUser;
import com.atguigu.security.login.ClientAddress;
import com.atguigu.security.login.LoginAttemptLimiter;
import com.atguigu.vo.system.LoginVo;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TokenLoginFilter extends UsernamePasswordAuthenticationFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtHelper jwtHelper;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public TokenLoginFilter(
            AuthenticationManager authenticationManager,
            JwtHelper jwtHelper,
            LoginAttemptLimiter loginAttemptLimiter
    ) {
        this.setAuthenticationManager(authenticationManager);
        this.jwtHelper = jwtHelper;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.setPostOnly(false);
        this.setRequiresAuthenticationRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/admin/system/index/login"));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response)
            throws AuthenticationException {
        LoginVo loginVo = readLoginVo(request);
        String username = loginVo.getUsername();
        String sourceAddress = ClientAddress.of(request);
        if (loginAttemptLimiter.isBlocked(username, sourceAddress)) {
            // Refused before the password is checked, so a locked account cannot be guessed.
            throw new LockedException("too many failed login attempts");
        }
        try {
            Authentication authenticationToken =
                    new UsernamePasswordAuthenticationToken(username, loginVo.getPassword());
            return this.getAuthenticationManager().authenticate(authenticationToken);
        } catch (AuthenticationException e) {
            if (!(e instanceof InternalAuthenticationServiceException)) {
                // A lookup or database outage is not a guessed password and must not count towards
                // the limit; a wrong password and an unknown username both arrive as BadCredentials.
                loginAttemptLimiter.recordFailure(username, sourceAddress);
            }
            throw e;
        }
    }

    private LoginVo readLoginVo(HttpServletRequest request) {
        try {
            return objectMapper.readValue(request.getInputStream(), LoginVo.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain,
                                            Authentication auth)
            throws IOException, ServletException {
        String username = auth.getName();
        String sourceAddress = ClientAddress.of(request);
        if (loginAttemptLimiter.isBlocked(username, sourceAddress)) {
            // A concurrent request may have locked the account while this password was checked.
            ResponseUtil.out(response, tooFrequent());
            return;
        }
        CustomUser customUser = (CustomUser) auth.getPrincipal();
        if (!AccountStatus.isEnabled(customUser.getSysUser().getStatus())) {
            ResponseUtil.out(response, Result.build(null, ResultCodeEnum.ACCOUNT_DISABLED));
            return;
        }
        loginAttemptLimiter.recordSuccess(username, sourceAddress);
        JwtHelper.TokenPair tokenPair = jwtHelper.createTokenPair(
                customUser.getSysUser().getId(),
                customUser.getSysUser().getUsername(),
                customUser.getSysUser().tokenVersionOrDefault());

        Map<String, Object> map = new HashMap<>();
        map.put("token", tokenPair.token());
        map.put("refreshToken", tokenPair.refreshToken());
        ResponseUtil.out(response, Result.ok(map));
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request,
                                              HttpServletResponse response,
                                              AuthenticationException failed)
            throws IOException, ServletException {
        if (failed instanceof LockedException) {
            ResponseUtil.out(response, tooFrequent());
            return;
        }
        ResponseUtil.out(response, Result.build(null, ResultCodeEnum.LOGIN_ERROR));
    }

    private static Result<Object> tooFrequent() {
        // Same code as a wrong password; only the advice differs, and the thresholds apply to
        // unknown usernames too, so the message never reveals whether the user exists.
        return Result.build(null, ResultCodeEnum.LOGIN_ERROR)
                .message("登录尝试过于频繁，请稍后再试");
    }
}
