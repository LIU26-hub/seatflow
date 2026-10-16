package com.seatflow.activity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An event that users can register for.
 *
 * <p>{@code registeredCount} is a denormalised counter. It is duplicated on
 * purpose: reading a seat count must not require counting rows, and milestone
 * M3 is where the invariant "registeredCount never exceeds capacity" becomes
 * the central problem of the project.
 *
 * <p>中文批注：活动实体，对应数据库的 activity 表。字段上的 {@code @Column} 显式声明列名与长度，
 * 它是"实体与表"之间的契约，启动时由 Hibernate 的 validate 校验，写错就直接起不来。
 * seatsLeft()、isWithinRegistrationWindow(LocalDateTime) 与 isOpenForRegistration(LocalDateTime)
 * 都是派生判断、不落库：第一个算余票并把下限截断到 0；第二个只回答"当前是否落在报名时间窗内"，
 * 语义是左闭右开（开始时刻算开放，结束时刻算关闭），由 ActivityTest 的六个边界用例固定；
 * 第三个回答业务问题"现在能不能报名"，组合了活动状态、时间窗与余票三个条件，
 * 对应产品文档的 BR-01、BR-02 与 BR-06。
 * 两个判断必须分开：如果只有合并后的业务方法，"正好开始""正好结束"这类边界会被状态与余票条件掩盖，
 * 窗口语义被改错也发现不了。
 * onCreate/onUpdate 是 JPA 生命周期回调，保存对象时自动维护创建时间与更新时间。
 */
@Entity
@Table(name = "activity")
@Getter
@Setter
@NoArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "location", nullable = false, length = 128)
    private String location;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "registered_count", nullable = false)
    private int registeredCount;

    @Column(name = "registration_start", nullable = false)
    private LocalDateTime registrationStart;

    @Column(name = "registration_end", nullable = false)
    private LocalDateTime registrationEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ActivityStatus status = ActivityStatus.DRAFT;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public int seatsLeft() {
        return Math.max(capacity - registeredCount, 0);
    }

    /**
     * Pure time-window predicate. The window is half-open, {@code [start, end)}:
     * the activity is already open at the start instant and already closed at
     * the end instant.
     *
     * <p>Package-private on purpose: only this entity and its tests need it.
     */
    boolean isWithinRegistrationWindow(LocalDateTime moment) {
        return !moment.isBefore(registrationStart)
                && moment.isBefore(registrationEnd);
    }

    /**
     * Business rule: whether someone can register for this activity at the
     * given moment. All three conditions must hold, so a published activity
     * that is full, or a draft activity inside its window, is not open.
     */
    public boolean isOpenForRegistration(LocalDateTime moment) {
        return status == ActivityStatus.PUBLISHED
                && isWithinRegistrationWindow(moment)
                && seatsLeft() > 0;
    }
}
