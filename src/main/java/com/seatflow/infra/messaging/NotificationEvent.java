package com.seatflow.infra.messaging;

/**
 * Event carried by a notification message. One value for now: a registration
 * was created and is waiting for confirmation.
 *
 * <p>中文批注：消息体里的业务事件类型。用枚举而不是字符串，生产者和消费者
 * 编译期就能对齐，不会因为一边写 "REGISTRATION_PENDING"、另一边写
 * "registration_pending" 而在运行时静默丢消息。
 */
public enum NotificationEvent {
    REGISTRATION_PENDING,
    REGISTRATION_CONFIRMED,
    REGISTRATION_CANCELLED,
    REGISTRATION_EXPIRED
}
