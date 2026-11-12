package com.seatflow.infra.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Durable topology for notification delivery.
 *
 * <pre>
 * seatflow.notification (topic)
 *   └── notification.registration.pending
 *         └── seatflow.notification.registration
 *               └── on rejection / TTL exhaustion
 *                     └── seatflow.notification.dlx (direct)
 *                           └── seatflow.notification.registration.dlq
 * </pre>
 *
 * <p>中文批注：所有队列都声明为 durable，消息默认持久化，broker 重启不会丢。
 * 主队列绑定死信交换机，消费失败超过重试次数后进入 DLQ，而不是无限重投把
 * 消费者卡死。声明式拓扑的好处是第一次连接时由应用自动创建，不需要手工
 * 在管理台建交换机与队列。
 *
 * <p>{@link Jackson2JsonMessageConverter} 让消息体是 JSON 而不是 JDK 序列化，
 * 消费端不必依赖生产端的类版本，也便于在管理台直接查看消息内容。
 */
@Configuration
public class RabbitMessagingConfig {

    public static final String NOTIFICATION_EXCHANGE = "seatflow.notification";
    public static final String NOTIFICATION_QUEUE = "seatflow.notification.registration";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.registration.pending";

    public static final String NOTIFICATION_DLX = "seatflow.notification.dlx";
    public static final String NOTIFICATION_DLQ = "seatflow.notification.registration.dlq";
    public static final String NOTIFICATION_DEAD_ROUTING_KEY = "notification.dead";

    // 主交换机，用于路由通知到队列
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE, true, false);
    }

    // 主队列，用于接收通知，指定死信交换机和路由键
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(NOTIFICATION_DLX)
                .deadLetterRoutingKey(NOTIFICATION_DEAD_ROUTING_KEY)
                .build();
    }

    // 主队列绑定主交换机，用于路由通知到队列
    @Bean
    public Binding notificationBinding(
            @Qualifier("notificationQueue") Queue queue,
            @Qualifier("notificationExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(NOTIFICATION_ROUTING_KEY);
    }

    // 死信交换机，用于路由死信通知到 DLQ
    @Bean
    public DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(NOTIFICATION_DLX, true, false);
    }

    // 死信队列，用于接收死信通知
    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    // 死信队列绑定死信交换机，用于路由死信通知到 DLQ
    @Bean
    public Binding notificationDeadLetterBinding(
            @Qualifier("notificationDeadLetterQueue") Queue queue,
            @Qualifier("notificationDeadLetterExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(NOTIFICATION_DEAD_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
