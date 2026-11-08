package com.seatflow.user;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.common.ApiResponse;
import com.seatflow.user.dto.AssignRoleRequest;
import com.seatflow.user.dto.UserResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Administrative user operations.
 *
 * <p>中文批注：路径以 /api/admin 开头，但真正的权限判断放在 UserRoleService，
 * 与其他模块保持一致：控制器只管绑定与包装，规则只有一处实现。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRoleService userRoleService;

    @PatchMapping("/{userId}/role")
    public ApiResponse<UserResponse> assignRole(@PathVariable Long userId,
                                                @Valid @RequestBody AssignRoleRequest request) {
        return ApiResponse.success(userRoleService.assignRole(userId, request.role()));
    }
}
