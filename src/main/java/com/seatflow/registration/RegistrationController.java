package com.seatflow.registration;

import com.seatflow.common.ApiResponse;
import com.seatflow.registration.dto.RegistrationResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/registration")
public class RegistrationController {
    private final RegistrationService registrationService;

    @PostMapping("/{activityId}/register")
    public ApiResponse<RegistrationResponse> registerActivity(@PathVariable Long activityId) {
        return ApiResponse.success(registrationService.registerActivity(activityId));
    }

    @PostMapping("/{activityId}/confirm")
    public ApiResponse<RegistrationResponse> confirmActivity(@PathVariable Long activityId) {
        return ApiResponse.success(registrationService.confirmActivity(activityId));
    }

    @PostMapping("/{activityId}/cancel")
    public ApiResponse<RegistrationResponse> cancelActivity(@PathVariable Long activityId) {
        return ApiResponse.success(registrationService.cancelActivity(activityId));
    }

    @GetMapping("/mine")
    public ApiResponse<List<RegistrationResponse>> getMyRegistration() {
        return ApiResponse.success(registrationService.getMyRegistration());
    }

}
