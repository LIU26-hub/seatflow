package com.seatflow.infra.messaging;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Publishes notification messages to the durable notification exchange.
 *
 * <p>中文批注：只负责把消息交给 RabbitMQ，不负责决定"该不该发"。
 * messageId 使用通知主键：消费端据此做幂等，重复投递不会产生两条站内信。
 *
 * <p>注意：这一步只是"已写入连接缓冲"，还没有拿到 broker 的确认。
 * 发件箱状态应当在 publisher confirm 回调成功后再标记为已发送；当前实现
 * 先保持简单，M5 接入 confirm 回调后再收紧这一步。
 */
@Component
@RequiredArgsConstructor
public class RabbitNotificationPublisher implements NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(NotificationMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMessagingConfig.NOTIFICATION_EXCHANGE,
                RabbitMessagingConfig.NOTIFICATION_ROUTING_KEY,
                message,
                amqpMessage -> {
                    amqpMessage.getMessageProperties()
                            .setMessageId(String.valueOf(message.notificationId()));
                    amqpMessage.getMessageProperties()
                            .setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    return amqpMessage;
                });
    }
}
