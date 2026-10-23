package com.seatflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.seatflow.infra.ratelimit.RegistrationRateLimitInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * Wires the registration rate limiter to the registration endpoint only.
 *
 * <p>中文批注：只挂在报名接口上。限流是一种业务选择，不是全局默认值——
 * 给所有接口都套上阈值，将来查询接口被正常高频调用时反而会误伤。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RegistrationRateLimitInterceptor registrationRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(registrationRateLimitInterceptor)
                .addPathPatterns("/api/activities/*/registrations");
    }
}
