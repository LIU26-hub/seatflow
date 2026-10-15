package com.seatflow.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers "what time is it" as a replaceable dependency.
 *
 * <p>中文批注：把"现在几点"注册成可替换的 Bean。这里显式固定为 Asia/Shanghai，不依赖运行环境的默认时区——
 * 容器默认是 UTC，如果跟随系统时区，报名窗口会晚 8 小时才关闭。
 * 测试里换成 Clock.fixed(...) 把时间钉死，边界用例（正好开始、正好结束）才有可能写出来。
 * 注意：这个包必须位于 com.seatflow 之下，否则 @SpringBootApplication 的组件扫描找不到它，
 * 启动会报 No qualifying bean of type 'java.time.Clock'。
 * 遗留提醒：实体里的 @PrePersist/@PreUpdate 仍在调用 LocalDateTime.now()（跟随 JVM 默认时区），
 * 所以 M4 给应用容器设置 TZ=Asia/Shanghai，否则审计时间戳会比业务时间少 8 小时。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
