package com.seatflow.activity.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What a client is allowed to send when creating an activity.
 *
 * <p>中文批注：这个 DTO 就是权限边界。实体里有 id、status、registeredCount、organizerId、
 * createdAt 这些字段，它们只能由服务端决定；直接接收实体的话，客户端就能改它们
 * （传 id 会走 merge 更新别人的活动，传 status 能跳过发布规则，传负数容量能破坏名额规则）。
 * 这里只保留组织者真正需要填的六个字段，其余全部在服务层赋值。
 *
 * <p>结构校验交给注解，跨字段规则（结束必须晚于开始）用 @AssertTrue：
 * 单个字段上的注解表达不了"两个字段之间的关系"，类级校验才是正确的位置。
 */
public record CreateActivityRequest(
        @NotBlank @Size(max = 128) String title,
        @Size(max = 1024) String description,
        @NotBlank @Size(max = 128) String location,
        @Min(1) int capacity,
        @NotNull LocalDateTime registrationStart,
        @NotNull LocalDateTime registrationEnd) {

    @AssertTrue(message = "registrationEnd must be after registrationStart")
    public boolean isRegistrationWindowValid() {
        return registrationStart == null
                || registrationEnd == null
                || registrationEnd.isAfter(registrationStart);
    }
}
