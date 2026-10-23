package com.seatflow.infra.scheduling;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.seatflow.config.RegistrationProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodic trigger for expiring overdue PENDING registrations.
 *
 * <p>中文批注：这里只做三件事——按配置的间隔触发、把批次大小传给处理器、捕获异常并记日志。
 * 异常必须捕获：@Scheduled 方法抛出异常时本轮执行直接中断，日志里只剩一条堆栈，
 * 很难分清是"扫描失败"还是"应用挂了"。
 * 用 ObjectProvider 取处理器，是为了在报名模块还没实现时也能正常启动（见接口注释）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationTimeoutJob {

    private final ObjectProvider<RegistrationExpiryHandler> handlerProvider;
    private final RegistrationProperties properties;

    @Scheduled(fixedDelayString = "${seatflow.registration.timeout-scan-interval:60s}")
    public void expireOverdueRegistrations() {
        RegistrationExpiryHandler handler = handlerProvider.getIfAvailable();
        if (handler == null) {
            log.debug("registration expiry handler not registered yet, skipping scan");
            return;
        }
        try {
            int expired = handler.expireOverdue(properties.getTimeoutBatchSize());
            if (expired > 0) {
                log.info("expired {} overdue registrations and released their seats", expired);
            }
        } catch (RuntimeException ex) {
            log.error("registration timeout scan failed", ex);
        }
    }
}
