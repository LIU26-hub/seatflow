package com.seatflow.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Business metrics that the HTTP-level defaults cannot express.
 *
 * <p>中文批注：http.server.requests 只能告诉你"接口被调了多少次、多快、返回了什么状态码"，
 * 但看不出"失败是因为满员、重复报名还是不在窗口"。这几个计数器把业务原因补上，
 * Grafana 才能画出有意义的报名结果分布。
 */
@Component
public class SeatFlowMetrics {

    private final MeterRegistry registry;

    public SeatFlowMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void registrationSucceeded() {
        counter("seatflow.registration", "outcome", "success").increment();
    }

    public void registrationRejected(String reason) {
        counter("seatflow.registration", "outcome", reason).increment();
    }

    public void notificationPublished() {
        counter("seatflow.notification.publish", "outcome", "sent").increment();
    }

    public void notificationRetried() {
        counter("seatflow.notification.publish", "outcome", "retry").increment();
    }

    public void notificationFailed() {
        counter("seatflow.notification.publish", "outcome", "failed").increment();
    }

    public void notificationDelivered() {
        counter("seatflow.notification", "outcome", "delivered").increment();
    }

    private Counter counter(String name, String tagKey, String tagValue) {
        return Counter.builder(name)
                .tag(tagKey, tagValue)
                .register(registry);
    }
}
