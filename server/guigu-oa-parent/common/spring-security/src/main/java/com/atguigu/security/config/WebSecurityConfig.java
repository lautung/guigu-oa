package com.atguigu.security.config;

import com.atguigu.common.jwt.JwtHelper;
import com.atguigu.security.custom.BcryptPasswordEncoderWithLegacyMd5;
import com.atguigu.security.custom.UserDetailsService;
import com.atguigu.security.filter.TokenAuthenticationFilter;
import com.atguigu.security.filter.TokenLoginFilter;
import com.atguigu.security.login.LoginAttemptLimiter;
import com.atguigu.security.login.LoginAttemptPolicy;
import com.atguigu.security.login.RedisLoginAttemptLimiter;
import com.atguigu.security.login.Slf4jLoginAudit;
import com.atguigu.security.revocation.RedisTokenRevocation;
import com.atguigu.security.revocation.TokenRevocation;
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
    public JwtHelper jwtHelper(@Value("${guigu.jwt.secret}") String secret) {
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
            UserDetailsService userDetailsService,
            AuthenticationManager authenticationManager,
            TokenRevocation tokenRevocation,
            LoginAttemptLimiter loginAttemptLimiter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PublicApiPaths.REQUEST_MATCHERS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new TokenAuthenticationFilter(redisTemplate, jwtHelper, userDetailsService, tokenRevocation),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilter(new TokenLoginFilter(authenticationManager, jwtHelper, loginAttemptLimiter))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
