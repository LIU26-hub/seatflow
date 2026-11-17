package com.seatflow.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Verifies the contract the dispatcher depends on: the message goes to the
 * configured exchange and routing key, and carries the notification id so the
 * consumer can de-duplicate a redelivery.
 */
@ExtendWith(MockitoExtension.class)
class RabbitNotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishesWithNotificationIdAndRoutingKey() throws Exception {
        RabbitNotificationPublisher publisher = new RabbitNotificationPublisher(rabbitTemplate);
        NotificationMessage message = new NotificationMessage(
                42L, 7L, 1L, 9L, NotificationEvent.REGISTRATION_PENDING);

        CompletableFuture<Boolean> confirmed = publisher.publish(message);

        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        then(rabbitTemplate).should().convertAndSend(
                eq(RabbitMessagingConfig.NOTIFICATION_EXCHANGE),
                eq(RabbitMessagingConfig.NOTIFICATION_ROUTING_KEY),
                eq(message),
                processor.capture(),
                correlation.capture());

        Message amqpMessage = processor.getValue().postProcessMessage(
                MessageBuilder.withBody(new byte[0]).build());

        assertThat(amqpMessage.getMessageProperties().getMessageId()).isEqualTo("42");
        assertThat(amqpMessage.getMessageProperties().getContentType())
                .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);

        correlation.getValue().getFuture().complete(new CorrelationData.Confirm(true, null));
        assertThat(confirmed.get(5, TimeUnit.SECONDS)).isTrue();
    }
}
