package com.seatflow.infra.ratelimit;

import java.time.Duration;

/**
 * Minimal counter abstraction so the limiter can be tested without Redis.
 *
 * <p>中文批注：限流器只依赖这个接口，不直接依赖 RedisTemplate。
 * 单元测试可以用一个 Map 实现它，不需要启动 Redis；生产实现放在 RedisCounterStore。
 * 这是"把外部依赖藏在接缝后面"的典型做法，将来换存储只改一个实现类。
 */
public interface CounterStore {

    /**
     * Atomically increments the counter for {@code key} and, when this call
     * creates the counter, gives it a TTL.
     *
     * @return the counter value after incrementing
     */
    long incrementAndGet(String key, Duration ttl);
}
