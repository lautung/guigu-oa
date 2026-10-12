package com.atguigu.security.filter;

import com.atguigu.common.jwt.JwtHelper;
import com.atguigu.common.result.ResponseUtil;
import com.atguigu.common.result.Result;
import com.atguigu.common.result.ResultCodeEnum;
import com.atguigu.security.config.PublicApiPaths;
import com.atguigu.security.custom.CustomUser;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.atguigu.security.custom.UserDetailsService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtHelper jwtHelper;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenAuthenticationFilter(
            RedisTemplate<String, String> redisTemplate,
            JwtHelper jwtHelper,
            UserDetailsService userDetailsService
    ) {
        this.redisTemplate = redisTemplate;
        this.jwtHelper = jwtHelper;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
        if (PublicApiPaths.permitsAnonymous(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        try {
            UsernamePasswordAuthenticationToken authentication = getAuthentication(request);
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                chain.doFilter(request, response);
            } else {
                ResponseUtil.out(response, Result.build(null, ResultCodeEnum.LOGIN_ERROR));
            }
        } catch (AccountDisabledException e) {
            SecurityContextHolder.clearContext();
            ResponseUtil.out(response, Result.build(null, ResultCodeEnum.ACCOUNT_DISABLED));
        } finally {
            LoginUserInfoHelper.removeUserId();
            LoginUserInfoHelper.removeUsername();
        }
    }

    private UsernamePasswordAuthenticationToken getAuthentication(HttpServletRequest request) {
        String token = request.getHeader("token");
        if (!StringUtils.hasText(token)) return null;

        String username = jwtHelper.getUsername(token);
        Long userId = jwtHelper.getUserId(token);
        if (!StringUtils.hasText(username) || userId == null) return null;

        CustomUser currentUser;
        try {
            currentUser = (CustomUser) userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            return null;
        }
        if (!Objects.equals(userId, currentUser.getSysUser().getId())) {
            return null;
        }
        if (currentUser.getSysUser().getStatus() == null || currentUser.getSysUser().getStatus() == 0) {
            throw new AccountDisabledException();
        }

        LoginUserInfoHelper.setUserId(userId);
        LoginUserInfoHelper.setUsername(username);

        String authString = redisTemplate.opsForValue().get(username);
        if (StringUtils.hasText(authString)) {
            try {
                List<Map<String, String>> mapList = objectMapper.readValue(
                        authString, new TypeReference<List<Map<String, String>>>() {});
                List<SimpleGrantedAuthority> authList = new ArrayList<>();
                for (Map<String, String> map : mapList) {
                    authList.add(new SimpleGrantedAuthority(map.get("authority")));
                }
                return new UsernamePasswordAuthenticationToken(username, null, authList);
            } catch (Exception e) {
                return new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());
            }
        }
        return new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());
    }

    private static final class AccountDisabledException extends RuntimeException {
    }
}
