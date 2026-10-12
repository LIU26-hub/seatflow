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
