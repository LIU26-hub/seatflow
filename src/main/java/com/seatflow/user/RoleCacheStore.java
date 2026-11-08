package com.seatflow.user;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache seam for the current role of a user.
 *
 * <p>中文批注：解析器只依赖这个接口，单元测试可以用内存实现，不需要启动 Redis。
 * 生产实现是 infra 包里的 RedisRoleCacheStore。
 */
public interface RoleCacheStore {

    Optional<UserRole> find(Long userId);

    void put(Long userId, UserRole role, Duration ttl);

    void evict(Long userId);
}
