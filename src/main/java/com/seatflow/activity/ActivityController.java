package com.seatflow.activity;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.activity.dto.ActivityResponse;
import com.seatflow.common.ApiResponse;
import lombok.RequiredArgsConstructor;

/**
 * HTTP layer only: bind the request, delegate to the service, wrap the result.
 * No business rules and no repository access in this class.
 *
 * <p>中文批注：HTTP 入口，只做三件事——绑定请求、委托服务、包装响应。
 * 这里不应出现任何业务规则：一旦把判断写进控制器，M4 的定时任务与消息消费者就无法复用同一套逻辑，
 * 只能复制一份，两份代码早晚会走歪。
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public ApiResponse<List<ActivityResponse>> listPublished() {
        return ApiResponse.success(activityService.listPublishedActivities());
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(activityService.getActivity(id));
    }
}
