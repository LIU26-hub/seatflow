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
 * seatsLeft() 与 isRegistrationOpen(LocalDateTime) 都是派生判断、不落库：前者算余票并把下限截断到 0，
 * 后者判断报名窗口是否开放（左闭右开——开始时刻算开放，结束时刻算关闭，这个语义由测试固定）。
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

    public boolean isRegistrationOpen(LocalDateTime moment) {
        return !moment.isBefore(registrationStart) && moment.isBefore(registrationEnd);
    }
}
