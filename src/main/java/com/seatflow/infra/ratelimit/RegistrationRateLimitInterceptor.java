package com.seatflow.infra.ratelimit;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.ApiResponse;
import com.seatflow.security.CurrentUser;
import com.seatflow.security.CurrentUserProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Applies the limiter to POST /api/activities/{id}/registrations.
 *
 * <p>中文批注：限流放在拦截器而不是服务层，因为它属于 HTTP 关注点（状态码、Retry-After 响应头），
 * 与业务规则无关；服务层即使被定时任务或消息消费者调用，也不该受"十秒内几次请求"这种限制。
 * 未登录请求直接放行给安全过滤器处理，否则限流器会先返回 429，
 * 把"没登录"误报成"请求太频繁"，让调用方排查方向跑偏。
 */
@Component
@RequiredArgsConstructor
public class RegistrationRateLimitInterceptor implements HandlerInterceptor {

    private final RegistrationRateLimiter rateLimiter;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        CurrentUser user = currentUserProvider.find().orElse(null);
        if (user == null) {
            return true;
        }
        RateLimitDecision decision = rateLimiter.check(user.id());
        if (decision.allowed()) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(429, decision.scope() + " rate limit exceeded, retry later"));
        return false;
    }
}
