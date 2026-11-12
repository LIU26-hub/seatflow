package com.seatflow.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Retry policy for the notification outbox.
 *
 * <p>中文批注：投递失败不能无限重试，否则 broker 长时间不可用时，同一批毒消息会
 * 一直占住扫描窗口。策略是"有限次数的指数退避"：每次失败后按
 * baseDelay * 2^(attempts-1) 推后重试，封顶 maxDelay，达到 maxAttempts 后
 * 转成 FAILED 终态，留给运维排查。
 */
@Component
@ConfigurationProperties(prefix = "seatflow.notification")
@Getter
@Setter
public class NotificationProperties {

    /** Rows fetched per dispatch tick. */
    private int batchSize = 100;

    /** Total publish attempts before a row becomes FAILED. */
    private int maxAttempts = 5;

    /** Delay before the second attempt; doubles on every further failure. */
    private Duration baseDelay = Duration.ofSeconds(5);

    /** Upper bound for the exponential backoff. */
    private Duration maxDelay = Duration.ofMinutes(5);
}
