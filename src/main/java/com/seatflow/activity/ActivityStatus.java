package com.seatflow.activity;

/**
 * Lifecycle of an activity.
 *
 * <p>Stored as a string in the database ({@code @Enumerated(EnumType.STRING)})
 * so that adding a new value never renumbers existing rows.
 *
 * <p>中文批注：活动状态枚举。配合 {@code @Enumerated(EnumType.STRING)} 以字符串入库，
 * 这样以后新增状态或调整枚举顺序，都不会改变历史数据原本的含义（用序号存储就会踩这个坑）。
 * 当前只有 PUBLISHED 会被列表接口查询出来。
 */
public enum ActivityStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED,
    FINISHED
}
