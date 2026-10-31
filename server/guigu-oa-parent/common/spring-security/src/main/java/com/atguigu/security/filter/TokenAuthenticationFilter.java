package com.atguigu.security.filter;

import com.atguigu.common.jwt.JwtHelper;
import com.atguigu.security.config.PublicApiPaths;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.atguigu.security.response.AuthenticationRejections;
import com.atguigu.security.session.SessionVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话核验交给 {@link SessionVerifier}（#173），本过滤器只负责把认出结果接入
 * Spring Security 上下文并从 Redis 组装权限，拒绝时经 AuthenticationRejections 写出。
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final SessionVerifier sessionVerifier;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenAuthenticationFilter(
            SessionVerifier sessionVerifier,
            RedisTemplate<String, String> redisTemplate
    ) {
        this.sessionVerifier = sessionVerifier;
        this.redisTemplate = redisTemplate;
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
            SessionVerifier.Outcome outcome =
                    sessionVerifier.verify(SessionVerifier.TokenKind.ACCESS, request.getHeader("token"));
            if (outcome instanceof SessionVerifier.Outcome.Rejected rejected) {
                SecurityContextHolder.clearContext();
                AuthenticationRejections.write(response, rejected.failure());
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(
                    buildAuthentication((SessionVerifier.Outcome.Recognized) outcome));
            chain.doFilter(request, response);
        } finally {
            LoginUserInfoHelper.removeUserId();
            LoginUserInfoHelper.removeUsername();
        }
    }

    private UsernamePasswordAuthenticationToken buildAuthentication(
            SessionVerifier.Outcome.Recognized recognized) {
        JwtHelper.TokenClaims claims = recognized.claims();
        LoginUserInfoHelper.setUserId(claims.userId());
        LoginUserInfoHelper.setUsername(claims.username());

        String authString = redisTemplate.opsForValue().get(claims.username());
        if (StringUtils.hasText(authString)) {
            try {
                List<Map<String, String>> mapList = objectMapper.readValue(
                        authString, new TypeReference<List<Map<String, String>>>() {});
                List<SimpleGrantedAuthority> authList = new ArrayList<>();
                for (Map<String, String> map : mapList) {
                    authList.add(new SimpleGrantedAuthority(map.get("authority")));
                }
                return new UsernamePasswordAuthenticationToken(claims.username(), null, authList);
            } catch (Exception e) {
                return new UsernamePasswordAuthenticationToken(claims.username(), null, new ArrayList<>());
            }
        }
        return new UsernamePasswordAuthenticationToken(claims.username(), null, new ArrayList<>());
    }
}
