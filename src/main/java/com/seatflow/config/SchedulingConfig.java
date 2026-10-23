package com.seatflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} so the registration timeout scanner runs.
 *
 * <p>中文批注：显式写在配置类里，而不是藏在启动类上，让"这个应用有后台任务"这件事可见。
 * M4 引入消息队列后，超时回收大概率从"定时扫描"演进为"延迟消息"，
 * 那时的改动入口也是这里和 RegistrationTimeoutJob，边界清楚。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
