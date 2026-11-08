package com.seatflow.infra.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.seatflow.user.RoleCacheStore;
import com.seatflow.user.UserRole;

import lombok.RequiredArgsConstructor;

/**
 * Redis backed role cache: one short string per user, with a TTL.
 *
 * <p>中文批注：键里带用户 id，值是角色名。TTL 只是兜底——正常路径下角色变更会主动删键，
 * 所以不需要等 TTL 过期。读到无法解析的脏值时顺手删掉，避免它一直命中。
 */
@Component
@RequiredArgsConstructor
public class RedisRoleCacheStore implements RoleCacheStore {

    private static final String KEY_PREFIX = "seatflow:auth:role:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Optional<UserRole> find(Long userId) {
        String value = stringRedisTemplate.opsForValue().get(key(userId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UserRole.valueOf(value));
        } catch (IllegalArgumentException ex) {
            stringRedisTemplate.delete(key(userId));
            return Optional.empty();
        }
    }

    @Override
    public void put(Long userId, UserRole role, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key(userId), role.name(), ttl);
    }

    @Override
    public void evict(Long userId) {
        stringRedisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
