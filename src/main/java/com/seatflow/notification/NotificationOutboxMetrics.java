package com.seatflow.notification;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Backlog gauges for the outbox.
 *
 * <p>中文批注：用量表暴露各状态行数，而不是在每次抓取指标时查数据库。
 * 30 秒刷新一次既能让面板反映积压趋势，又不会给数据库增加持续压力。
 */
@Component
public class NotificationOutboxMetrics {

    private final NotificationRepository notificationRepository;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public NotificationOutboxMetrics(NotificationRepository notificationRepository, MeterRegistry registry) {
        this.notificationRepository = notificationRepository;
        Gauge.builder("seatflow.notification.outbox", pending, AtomicLong::get)
                .tag("status", "PENDING").register(registry);
        Gauge.builder("seatflow.notification.outbox", failed, AtomicLong::get)
                .tag("status", "FAILED").register(registry);
    }

    @Scheduled(fixedDelayString = "${seatflow.notification.metrics-interval:30s}")
    public void refresh() {
        pending.set(notificationRepository.countByStatus(NotificationStatus.PENDING));
        failed.set(notificationRepository.countByStatus(NotificationStatus.FAILED));
    }
}
