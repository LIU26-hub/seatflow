package com.seatflow.infra.gate;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis backed seat gate.
 *
 * <p>中文批注：初始化与扣减必须在同一个 Lua 脚本里完成。如果先 SETNX 再 DECR，
 * 两个并发请求可能都看到"键不存在"，各自按旧的余票初始化，导致闸门放行超过容量。
 * 脚本内部先判断键是否存在，不存在才按传入的数据库快照初始化，然后立即扣减，
 * 整个过程在 Redis 里原子完成。
 *
 * <p>返回值用负数表示"已售罄"，因为扣减成功后剩余数量也可能是 0，两者不能混淆。
 * Redis 不可用时一律放行（fail-open）：闸门只优化性能，不能成为报名可用性的单点。
 */
@Slf4j
@Component
public class RedisSeatGate implements SeatGate {

    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>(
            """
            local current = redis.call('GET', KEYS[1])
            if not current then
                redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
                current = ARGV[1]
            end
            current = tonumber(current)
            if current <= 0 then
                return -1
            end
            return redis.call('DECR', KEYS[1])
            """,
            Long.class);

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            return redis.call('INCR', KEYS[1])
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration keyTtl;

    public RedisSeatGate(StringRedisTemplate redisTemplate,
                         @Value("${seatflow.seat-gate.key-ttl:24h}") Duration keyTtl) {
        this.redisTemplate = redisTemplate;
        this.keyTtl = keyTtl;
    }

    @Override
    public boolean tryReserve(Long activityId, int seatsLeftSnapshot) {
        try {
            Long remaining = redisTemplate.execute(
                    RESERVE,
                    List.of(key(activityId)),
                    String.valueOf(Math.max(seatsLeftSnapshot, 0)),
                    String.valueOf(keyTtl.getSeconds()));
            return remaining != null && remaining >= 0;
        } catch (RuntimeException ex) {
            log.warn("seat gate unavailable for activity {}, falling back to database", activityId, ex);
            return true;
        }
    }

    @Override
    public void release(Long activityId) {
        try {
            redisTemplate.execute(RELEASE, List.of(key(activityId)));
        } catch (RuntimeException ex) {
            log.warn("failed to release seat gate for activity {}", activityId, ex);
        }
    }

    private String key(Long activityId) {
        return "seatflow:gate:activity:" + activityId + ":seats";
    }
}
