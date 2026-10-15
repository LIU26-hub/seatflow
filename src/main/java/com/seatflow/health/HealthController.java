package com.seatflow.health;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.common.ApiResponse;

/**
 * Lightweight liveness endpoint owned by the application itself.
 *
 * <p>Spring Boot Actuator already exposes {@code /actuator/health}. This
 * endpoint exists so the project keeps a tiny, dependency free controller that
 * proves the web layer, the response envelope and the JSON setup all work.
 *
 * <p>中文批注：应用自身的存活探针。刻意不依赖数据库，只用来快速判断"进程活着、web 层可用"；
 * 数据库连通性由 /actuator/health 负责，两者分工不同——业务故障时存活探针仍然要能回答。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("application", applicationName);
        body.put("timestamp", OffsetDateTime.now().toString());
        return ApiResponse.success(body);
    }
}
