package com.seatflow.infra.messaging;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Publishes notification messages to the durable notification exchange.
 *
 * <p>中文批注：只负责把消息交给 RabbitMQ，不负责决定"该不该发"。
 * messageId 使用通知主键：消费端据此做幂等，重复投递不会产生两条站内信。
 *
 * <p>注意：这一步通过 publisher confirm 闭环——{@code convertAndSend} 返回
 * 只代表消息进入连接缓冲，最终是否被 broker 接收由 {@link CorrelationData}
 * 的 confirm 回调决定；调用方必须等到 ack 才能把发件箱行标记为已发送。
 */
@Component
@RequiredArgsConstructor
public class RabbitNotificationPublisher implements NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public CompletableFuture<Boolean> publish(NotificationMessage message) {
        CorrelationData correlationData = new CorrelationData(String.valueOf(message.notificationId()));
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
                },
                correlationData);
        return correlationData.getFuture()
                .thenApply(CorrelationData.Confirm::isAck)
                .completeOnTimeout(false, 5, TimeUnit.SECONDS);
    }
}
