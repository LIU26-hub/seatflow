package com.seatflow.infra.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Redis backed counter. INCR and PEXPIRE run inside one script so a crash
 * between the two commands can never leave a counter without a TTL.
 *
 * <p>中文批注：为什么必须用 Lua：如果先 INCR 再单独 PEXPIRE，两次调用之间断电或网络抖动，
 * 就会留下一个永不过期的计数器，那个用户会被永久限流。脚本在 Redis 内部原子执行，没有这个窗口。
 * 只在计数为 1（本次刚创建键）时设置过期时间，避免每次请求都把窗口往后推——
 * 那会变成滑动窗口，与配置里的固定窗口语义不一致。
 */
@Component
@RequiredArgsConstructor
public class RedisCounterStore implements CounterStore {

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public long incrementAndGet(String key, Duration ttl) {
        Long current = stringRedisTemplate.execute(
                INCREMENT_WITH_TTL,
                List.of(key),
                String.valueOf(ttl.toMillis()));
        if (current == null) {
            throw new IllegalStateException("redis returned no value for counter " + key);
        }
        return current;
    }
}
