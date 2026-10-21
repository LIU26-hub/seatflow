package com.seatflow.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Makes JPA fill in createdAt and updatedAt from the injected clock.
 *
 * <p>中文批注：手写 @PrePersist 有两个问题。一是它只能调 LocalDateTime.now()，
 * 跟随 JVM 默认时区，和业务时间用的 Asia/Shanghai 不是同一个来源；二是每个实体都要复制一遍
 * 同样的几行代码。换成 Spring Data 的审计机制之后，时间只有一个来源（注入的 Clock），
 * 测试里把它固定住，审计时间戳也能被断言。
 *
 * <p>实体需要配合三件事：类上加 @EntityListeners(AuditingEntityListener.class)、
 * 字段上标 @CreatedDate 与 @LastModifiedDate、并且对象必须经由仓储保存。
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
