package com.seatflow.infra.messaging;

/**
 * Wire contract between the outbox dispatcher and the notification consumer.
 *
 * <p>This record is deliberately flat and free of entities: the message must be
 * reconstructable from the database row alone, because the consumer may run in
 * a different process minutes after the registration transaction committed.
 *
 * <p>中文批注：这是队列里真正传输的数据形状，不引用任何实体。消费者可能在几秒
 * 或几分钟后、在另一个进程里处理它，因此消息必须自包含：拿到这五个字段就能
 * 完成通知，不需要再回查注册事务当时的对象状态。
 *
 * <p>{@code notificationId} 同时用作 AMQP 的 messageId。消费者以它做幂等键，
 * 同一条通知重复投递时只处理一次。
 */
public record NotificationMessage(
        Long notificationId,
        Long registrationId,
        Long activityId,
        Long userId,
        NotificationEvent event) {
}
