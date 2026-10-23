package com.seatflow.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Registration settings that must be tunable without a code change.
 *
 * <p>中文批注：把"确认时限"和"限流阈值"放进配置，而不是写死在代码里。
 * 确认时限在产品文档 BR-08 里明确是可配置参数，默认 15 分钟只是占位值（风险 R-05）；
 * 限流阈值同样需要在上线后按真实流量校准，所以用 Duration 而不是毫秒常量表达：
 * 配置写成 15m、10s，比 900000 直观得多。
 */
@Component
@ConfigurationProperties(prefix = "seatflow.registration")
@Getter
@Setter
public class RegistrationProperties {

    /** How long a PENDING registration holds a seat. */
    private Duration confirmTtl = Duration.ofMinutes(15);

    /** How often the timeout scanner looks for overdue PENDING records. */
    private Duration timeoutScanInterval = Duration.ofSeconds(60);

    /** How many overdue records one scan may process. */
    private int timeoutBatchSize = 100;

    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class RateLimit {

        private int userMaxRequests = 5;
        private Duration userWindow = Duration.ofSeconds(10);
        private int globalMaxRequests = 200;
        private Duration globalWindow = Duration.ofSeconds(1);
    }
}
