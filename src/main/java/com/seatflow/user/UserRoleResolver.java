package com.seatflow.user;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the current role of a user on every authenticated request.
 *
 * <p>中文批注：顺序是先缓存、再数据库。缓存命中时只花一次 Redis GET；
 * 未命中时查库并把结果写回缓存。Redis 不可用时退回数据库，而不是让请求失败——
 * 角色是鉴权数据，宁可多查一次库，也不能把所有人挡在门外。
 * 另外加了一个 5 秒的熔断：Redis 第一次异常后，这 5 秒内直接走数据库，
 * 避免每个请求都先等一次 200 毫秒的 Redis 超时。
 */
@Slf4j
@Component
public class UserRoleResolver {

    private static final long REDIS_BACKOFF_MILLIS = 5_000L;

    private final RoleCacheStore roleCacheStore;
    private final UserRepository userRepository;
    private final Clock clock;
    private final Duration cacheTtl;
    private final AtomicLong redisSkippedUntil = new AtomicLong(0);

    public UserRoleResolver(RoleCacheStore roleCacheStore,
                            UserRepository userRepository,
                            Clock clock,
                            @Value("${seatflow.security.role-cache.ttl:30m}") Duration cacheTtl) {
        this.roleCacheStore = roleCacheStore;
        this.userRepository = userRepository;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
    }

    public Optional<UserRole> resolve(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        if (redisUsable()) {
            try {
                Optional<UserRole> cached = roleCacheStore.find(userId);
                if (cached.isPresent()) {
                    return cached;
                }
            } catch (RuntimeException ex) {
                backOffRedis(ex);
            }
        }

        Optional<UserRole> fromDatabase = userRepository.findById(userId).map(AppUser::getRole);
        fromDatabase.ifPresent(role -> cacheQuietly(userId, role));
        return fromDatabase;
    }

    /** Called after a role change commits, so the next request sees the new value. */
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            roleCacheStore.evict(userId);
            redisSkippedUntil.set(0);
        } catch (RuntimeException ex) {
            log.warn("failed to evict role cache for user {}: {}", userId, ex.getMessage());
        }
    }

    private void cacheQuietly(Long userId, UserRole role) {
        if (!redisUsable()) {
            return;
        }
        try {
            roleCacheStore.put(userId, role, cacheTtl);
        } catch (RuntimeException ex) {
            backOffRedis(ex);
        }
    }

    private boolean redisUsable() {
        return clock.millis() >= redisSkippedUntil.get();
    }

    private void backOffRedis(RuntimeException ex) {
        redisSkippedUntil.set(clock.millis() + REDIS_BACKOFF_MILLIS);
        log.warn("role cache unavailable, falling back to database for {} ms: {}",
                REDIS_BACKOFF_MILLIS, ex.getMessage());
    }
}
