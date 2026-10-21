package com.seatflow.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Reads the bearer token and, when it is valid, marks the request as
 * authenticated.
 *
 * <p>中文批注：过滤器只做一件事——令牌有效就把身份放进 SecurityContext，无效就当没登录。
 * 它不负责拒绝请求：拒绝交给授权规则去做，这样未登录访问受保护接口会统一走RestAuthenticationEntryPoint，所有 401 的响应格式都相同
 * RestAuthenticationEntryPoint 返回 401，而不是在这里各写一套响应格式。
 * 角色写成 ROLE_ 前缀的权限（ROLE_ORGANIZER 等），这是 Spring Security 的约定写法。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        bearerToken(request)
                .flatMap(tokenService::parse)
                .ifPresent(user -> authenticate(request, user));
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, CurrentUser user) {
        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
