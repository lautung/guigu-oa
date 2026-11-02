package com.lautung.oa.security.config;

import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.security.custom.BcryptPasswordEncoderWithLegacyMd5;
import com.lautung.oa.security.custom.RestAccessDeniedHandler;
import com.lautung.oa.security.custom.RestAuthenticationEntryPoint;
import com.lautung.oa.security.custom.UserDetailsService;
import com.lautung.oa.security.filter.TokenAuthenticationFilter;
import com.lautung.oa.security.filter.TokenLoginFilter;
import com.lautung.oa.security.login.LoginAttemptLimiter;
import com.lautung.oa.security.login.LoginAttemptPolicy;
import com.lautung.oa.security.login.RedisLoginAttemptLimiter;
import com.lautung.oa.security.login.Slf4jLoginAudit;
import com.lautung.oa.security.revocation.RedisTokenRevocation;
import com.lautung.oa.security.revocation.TokenRevocation;
import com.lautung.oa.security.session.SessionVerifier;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig {

    @Bean
    public JwtHelper jwtHelper(@Value("${lautung.jwt.secret}") String secret) {
        return new JwtHelper(secret);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BcryptPasswordEncoderWithLegacyMd5();
    }

    @Bean
    public TokenRevocation tokenRevocation(RedisTemplate<String, String> redisTemplate) {
        return new RedisTokenRevocation(redisTemplate);
    }

    /** 会话核验 module（#173）：过滤器与续期端点共用的唯一凭证判定。 */
    @Bean
    public SessionVerifier sessionVerifier(
            JwtHelper jwtHelper,
            TokenRevocation tokenRevocation,
            UserDetailsService userDetailsService
    ) {
        return new SessionVerifier(jwtHelper, tokenRevocation, userDetailsService);
    }

    @Bean
    public Clock loginAttemptClock() {
        return Clock.systemUTC();
    }

    @Bean
    public LoginAttemptLimiter loginAttemptLimiter(
            RedisTemplate<String, String> redisTemplate,
            Clock loginAttemptClock
    ) {
        return new RedisLoginAttemptLimiter(
                redisTemplate,
                LoginAttemptPolicy.account(),
                LoginAttemptPolicy.source(),
                new Slf4jLoginAudit(),
                loginAttemptClock);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            UserDetailsPasswordService userDetailsPasswordService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setUserDetailsPasswordService(userDetailsPasswordService);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            RedisTemplate<String, String> redisTemplate,
            JwtHelper jwtHelper,
            SessionVerifier sessionVerifier,
            AuthenticationManager authenticationManager,
            LoginAttemptLimiter loginAttemptLimiter,
            Validator validator
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // 服务端不接跨源：统一客户端经 nginx 的 /dev-api 同源代理访问 API，
                // 跨源需求由入口网关按受信来源显式放行（#141）。
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PublicApiPaths.REQUEST_MATCHERS).permitAll()
                        .anyRequest().authenticated()
                )
                // #145：未认证一律 401 + code 208（含匿名路径上的错误方法，不再 403 空体），
                // 已认证但被安全层拒绝一律 403 + code 205。
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .addFilterBefore(
                        new TokenAuthenticationFilter(sessionVerifier, redisTemplate),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilter(new TokenLoginFilter(authenticationManager, jwtHelper, loginAttemptLimiter, validator))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
