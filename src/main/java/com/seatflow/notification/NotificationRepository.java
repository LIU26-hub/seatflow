package com.seatflow.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Dispatcher scans pending rows oldest first, so a batch never starves the
     * earliest notifications and the order is deterministic in tests.
     */
    List<Notification> findByStatusOrderByIdAsc(NotificationStatus status, Pageable pageable);

    /** 站内信列表：用户自己的通知，按时间倒序。 */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 发件箱阶段：PENDING -> SENT。条件更新保证并发安全，返回影响行数。
     * 返回 0 说明行已被消费端抢先改成 DELIVERED（竞态下的正常结果）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Notification n
              set n.status = :target, n.sentAt = :now
            where n.id = :id and n.status = :expected
           """)
    int markSent(@Param("id") Long id,
                 @Param("target") NotificationStatus target,
                 @Param("expected") NotificationStatus expected,
                 @Param("now") LocalDateTime now);

    /**
     * 消费落地：PENDING/SENT -> DELIVERED。幂等核心——重复消息命中同一行时，
     * 条件不再满足，返回 0，调用方据此跳过，不会重复处理。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Notification n
              set n.status = :target, n.deliveredAt = :now
            where n.id = :id and n.status in :sources
           """)
    int markDelivered(@Param("id") Long id,
                      @Param("target") NotificationStatus target,
                      @Param("sources") Collection<NotificationStatus> sources,
                      @Param("now") LocalDateTime now);
}
