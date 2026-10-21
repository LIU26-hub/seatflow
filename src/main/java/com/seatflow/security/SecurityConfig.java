package com.seatflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * HTTP security rules for a stateless JSON API.
 *
 * <p>中文批注：这里只做"粗粒度"的门禁——登录接口和健康检查放行，其余一律要求先通过认证。
 * 角色与归属这类"细粒度"判断刻意不写在这里，而是放在服务层（见产品文档 7.5 节）：
 * 定时任务与消息消费者将来也会调同一批服务方法，规则必须只有一处实现，否则迟早会分叉。
 *
 * <p>三个关键开关：关闭 CSRF（无 Cookie 会话，令牌放在请求头里，CSRF 不适用）；
 * 会话策略设为 STATELESS（不建 HttpSession，每个请求都靠令牌自证）；
 * 关掉 httpBasic 与表单登录（否则浏览器访问会弹原生登录框，污染接口契约）。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/**", "/api/health", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * BCrypt rather than a plain hash: it salts automatically and is deliberately
     * slow, which is what makes offline brute force expensive.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
