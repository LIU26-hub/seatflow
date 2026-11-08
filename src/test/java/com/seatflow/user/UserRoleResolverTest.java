package com.seatflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the role resolver, with an in-memory cache store.
 *
 * <p>中文批注：不需要 Redis、不需要 Spring。四个用例分别钉住：缓存命中不查库、
 * 缓存未命中查库并回填、Redis 异常时仍能从数据库拿到角色、清除后下一次回源。
 */
class UserRoleResolverTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-08T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final Duration TTL = Duration.ofMinutes(30);

    private FakeStore store;
    private UserRepository userRepository;
    private UserRoleResolver resolver;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        userRepository = mock(UserRepository.class);
        resolver = new UserRoleResolver(store, userRepository, CLOCK, TTL);
    }

    @Test
    void returnsCachedRoleWithoutQueryingTheDatabase() {
        store.roles.put(1L, UserRole.ORGANIZER);

        assertThat(resolver.resolve(1L)).contains(UserRole.ORGANIZER);
        verifyNoInteractions(userRepository);
    }

    @Test
    void loadsFromDatabaseAndCachesOnMiss() {
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L, UserRole.PARTICIPANT)));

        assertThat(resolver.resolve(2L)).contains(UserRole.PARTICIPANT);
        assertThat(store.roles).containsEntry(2L, UserRole.PARTICIPANT);
    }

    @Test
    void fallsBackToDatabaseWhenRedisFails() {
        store.failOnRead = true;
        given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, UserRole.ORGANIZER)));

        assertThat(resolver.resolve(3L)).contains(UserRole.ORGANIZER);
        assertThat(store.putCount).isZero();
    }

    @Test
    void evictedRoleIsReloadedOnTheNextCall() {
        store.roles.put(4L, UserRole.PARTICIPANT);
        given(userRepository.findById(4L)).willReturn(Optional.of(user(4L, UserRole.ORGANIZER)));

        resolver.evict(4L);

        assertThat(resolver.resolve(4L)).contains(UserRole.ORGANIZER);
        verify(userRepository).findById(4L);
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        return user;
    }

    private static final class FakeStore implements RoleCacheStore {
        private final Map<Long, UserRole> roles = new HashMap<>();
        private boolean failOnRead;
        private int putCount;

        @Override
        public Optional<UserRole> find(Long userId) {
            if (failOnRead) {
                throw new IllegalStateException("redis is down");
            }
            return Optional.ofNullable(roles.get(userId));
        }

        @Override
        public void put(Long userId, UserRole role, Duration ttl) {
            putCount++;
            roles.put(userId, role);
        }

        @Override
        public void evict(Long userId) {
            roles.remove(userId);
        }
    }
}
