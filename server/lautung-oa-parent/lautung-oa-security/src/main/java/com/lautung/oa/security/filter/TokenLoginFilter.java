package com.lautung.oa.security.filter;

import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.common.result.ResponseUtil;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.common.validation.ValidationMessages;
import com.lautung.oa.security.custom.AccountStatus;
import com.lautung.oa.security.custom.CustomUser;
import com.lautung.oa.security.login.ClientAddress;
import com.lautung.oa.security.login.InvalidLoginInputException;
import com.lautung.oa.security.login.LoginAttemptLimiter;
import com.lautung.oa.security.response.AuthenticationFailure;
import com.lautung.oa.security.response.AuthenticationRejections;
import com.lautung.oa.vo.system.LoginVo;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tools.jackson.core.JacksonException;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TokenLoginFilter extends UsernamePasswordAuthenticationFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtHelper jwtHelper;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final Validator validator;

    public TokenLoginFilter(
            AuthenticationManager authenticationManager,
            JwtHelper jwtHelper,
            LoginAttemptLimiter loginAttemptLimiter,
            Validator validator
    ) {
        this.setAuthenticationManager(authenticationManager);
        this.jwtHelper = jwtHelper;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.validator = validator;
        this.setPostOnly(false);
        this.setRequiresAuthenticationRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/admin/system/index/login"));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response)
            throws AuthenticationException {
        LoginVo loginVo = readLoginVo(request);
        requireValid(loginVo);
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
            LoginVo loginVo = objectMapper.readValue(request.getInputStream(), LoginVo.class);
            return loginVo == null ? new LoginVo() : loginVo;
        } catch (IOException | JacksonException e) {
            throw new InvalidLoginInputException("请输入用户名和密码");
        }
    }

    private void requireValid(LoginVo loginVo) {
        Set<ConstraintViolation<LoginVo>> violations = validator.validate(loginVo);
        if (violations.isEmpty()) {
            return;
        }
        String message = ValidationMessages.join(violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .toList());
        throw new InvalidLoginInputException(message);
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
            AuthenticationRejections.write(response, AuthenticationFailure.INVALID_TOKEN, TOO_FREQUENT_MESSAGE);
            return;
        }
        CustomUser customUser = (CustomUser) auth.getPrincipal();
        if (!AccountStatus.isEnabled(customUser.getSysUser().getStatus())) {
            AuthenticationRejections.write(response, AuthenticationFailure.ACCOUNT_DISABLED);
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
        if (failed instanceof InvalidLoginInputException invalidInput) {
            AuthenticationRejections.write(response, AuthenticationFailure.INVALID_TOKEN, invalidInput.getMessage());
            return;
        }
        if (failed instanceof LockedException) {
            AuthenticationRejections.write(response, AuthenticationFailure.INVALID_TOKEN, TOO_FREQUENT_MESSAGE);
            return;
        }
        AuthenticationRejections.write(response, AuthenticationFailure.INVALID_TOKEN);
    }

    /**
     * Same code as a wrong password; only the advice differs, and the thresholds apply to
     * unknown usernames too, so the message never reveals whether the user exists.
     */
    private static final String TOO_FREQUENT_MESSAGE = "登录尝试过于频繁，请稍后再试";
}
