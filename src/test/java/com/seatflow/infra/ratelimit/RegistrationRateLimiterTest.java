package com.seatflow.infra.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seatflow.config.RegistrationProperties;

/**
 * Unit tests for the fixed window limiter, with an in-memory counter store.
 *
 * <p>中文批注：不需要 Redis、不需要 Spring，因为限流器只依赖 CounterStore 这个接缝。
 * 第一个用例钉住"同一用户前 5 次通过、第 6 次拒绝"；第二个钉住"单个用户额度没超时，
 * 全局额度也会拦截"；第三个钉住"存储不可用时放行"，这是产品文档 R-02 的降级要求。
 * 时钟用固定值注入，窗口键才稳定，测试不会在整秒边界上偶发失败。
 */
class RegistrationRateLimiterTest {

    private static final long NOW = 1_700_000_000_000L;

    private Map<String, AtomicLong> counters;
    private RegistrationProperties properties;

    @BeforeEach
    void setUp() {
        counters = new HashMap<>();
        properties = new RegistrationProperties();
        properties.getRateLimit().setUserMaxRequests(5);
        properties.getRateLimit().setUserWindow(Duration.ofSeconds(10));
        properties.getRateLimit().setGlobalMaxRequests(200);
        properties.getRateLimit().setGlobalWindow(Duration.ofSeconds(1));
    }

    @Test
    void deniesTheSixthRequestFromTheSameUser() {
        RegistrationRateLimiter limiter = limiter((key, ttl) -> counters
                .computeIfAbsent(key, ignored -> new AtomicLong())
                .incrementAndGet());

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.check(7L).allowed()).as("request %d", i + 1).isTrue();
        }

        RateLimitDecision denied = limiter.check(7L);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.scope()).isEqualTo("user");
        assertThat(denied.retryAfterSeconds()).isEqualTo(10);
    }

    @Test
    void deniesWhenTheGlobalLimitIsReached() {
        properties.getRateLimit().setGlobalMaxRequests(2);
        RegistrationRateLimiter limiter = limiter((key, ttl) -> counters
                .computeIfAbsent(key, ignored -> new AtomicLong())
                .incrementAndGet());

        assertThat(limiter.check(1L).allowed()).isTrue();
        assertThat(limiter.check(2L).allowed()).isTrue();

        RateLimitDecision denied = limiter.check(3L);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.scope()).isEqualTo("global");
    }

    @Test
    void allowsRequestsWhenTheCounterStoreIsUnavailable() {
        RegistrationRateLimiter limiter = limiter((key, ttl) -> {
            throw new IllegalStateException("redis is down");
        });

        assertThat(limiter.check(1L).allowed()).isTrue();
    }

    private RegistrationRateLimiter limiter(CounterStore store) {
        return new RegistrationRateLimiter(store, properties, () -> NOW);
    }
}
