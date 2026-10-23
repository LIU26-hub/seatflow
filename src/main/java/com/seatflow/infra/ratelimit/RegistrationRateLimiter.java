package com.seatflow.infra.ratelimit;

import java.time.Duration;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.seatflow.config.RegistrationProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Fixed window limiter for the registration endpoint: one limit per user and
 * one global limit, both configurable.
 *
 * <p>中文批注：先查用户额度、再查全局额度，顺序有意为之——如果先查全局，
 * 一个脚本刷请求就能把所有人共用的全局额度耗光；先拦单个用户，全局额度才代表整体流量。
 * 窗口键里带上"当前毫秒数除以窗口长度"的整数商，窗口一到键自然换新，不需要定时清理。
 * 失败策略是 fail-open：Redis 不可用时记录 warn 并放行。限流是保护措施，不是业务正确性，
 * 让 Redis 故障把报名主链路一起拖垮，与产品文档 R-02 的降级要求相悖。
 */
@Slf4j
@Component
public class RegistrationRateLimiter {

    private static final String USER_KEY_PREFIX = "rl:reg:user:";
    private static final String GLOBAL_KEY_PREFIX = "rl:reg:global:";

    private final CounterStore counterStore;
    private final RegistrationProperties properties;
    private final LongSupplier clockMillis;

    @Autowired
    public RegistrationRateLimiter(CounterStore counterStore, RegistrationProperties properties) {
        this(counterStore, properties, System::currentTimeMillis);
    }

    /** Visible for tests: the window key has to be deterministic. */
    RegistrationRateLimiter(CounterStore counterStore, RegistrationProperties properties, LongSupplier clockMillis) {
        this.counterStore = counterStore;
        this.properties = properties;
        this.clockMillis = clockMillis;
    }

    public RateLimitDecision check(Long userId) {
        RegistrationProperties.RateLimit limits = properties.getRateLimit();
        long now = clockMillis.getAsLong();
        try {
            RateLimitDecision userDecision = evaluate(
                    USER_KEY_PREFIX + userId + ":" + windowKey(now, limits.getUserWindow()),
                    limits.getUserMaxRequests(),
                    limits.getUserWindow(),
                    "user");
            if (!userDecision.allowed()) {
                return userDecision;
            }
            return evaluate(
                    GLOBAL_KEY_PREFIX + windowKey(now, limits.getGlobalWindow()),
                    limits.getGlobalMaxRequests(),
                    limits.getGlobalWindow(),
                    "global");
        } catch (RuntimeException ex) {
            log.warn("rate limiter unavailable, allowing registration request: {}", ex.getMessage());
            return RateLimitDecision.allow();
        }
    }

    private RateLimitDecision evaluate(String key, int maxRequests, Duration window, String scope) {
        long current = counterStore.incrementAndGet(key, window);
        if (current <= maxRequests) {
            return RateLimitDecision.allow();
        }
        return RateLimitDecision.deny(scope, window.toSeconds());
    }

    private long windowKey(long nowMillis, Duration window) {
        return nowMillis / window.toMillis();
    }
}
