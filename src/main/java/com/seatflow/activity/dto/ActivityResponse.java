package com.seatflow.activity.dto;

import java.time.LocalDateTime;

import com.seatflow.activity.Activity;
import com.seatflow.activity.ActivityStatus;

/**
 * What the API exposes for an activity.
 *
 * <p>The entity is never returned directly: the wire format is a deliberate
 * contract, and it carries a derived field ({@code seatsLeft}) that does not
 * exist in the database.
 *
 * <p>中文批注：对外接口契约。控制器永远不返回实体：实体跟随数据库结构变化，DTO 跟随接口契约变化，
 * 两者节奏不同（数据库加一列不该改变接口格式）。seatsLeft 与 registrationOpen 都是派生字段，
 * 数据库里不存在，读的时候算出来。工厂方法接受 now 参数而不在内部调用 now()，
 * 这样"正好开始/正好结束"这些边界行为才可能被测试固定下来。
 * 注意：record 组件已有 11 个，from() 里那 11 行的顺序是唯一的维护点，加字段时别错位。
 */
public record ActivityResponse(
        Long id,
        Long organizerId,
        String title,
        String description,
        String location,
        int capacity,
        int registeredCount,
        int seatsLeft,
        boolean registrationOpen,
        LocalDateTime registrationStart,
        LocalDateTime registrationEnd,
        ActivityStatus status) {

    public static ActivityResponse from(Activity activity, LocalDateTime now) {
        return new ActivityResponse(
                activity.getId(),
                activity.getOrganizerId(),
                activity.getTitle(),
                activity.getDescription(),
                activity.getLocation(),
                activity.getCapacity(),
                activity.getRegisteredCount(),
                activity.seatsLeft(),
                activity.isOpenForRegistration(now),
                activity.getRegistrationStart(),
                activity.getRegistrationEnd(),
                activity.getStatus());
    }
}
