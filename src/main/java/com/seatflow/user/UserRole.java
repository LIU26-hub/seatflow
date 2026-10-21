package com.seatflow.user;

/**
 * Who the caller is, as far as authorisation is concerned.
 *
 * <p>中文批注：角色和 CurrentUser 一起放在 user 包，因为它们是身份模型的一部分；
 * security 包单向依赖这里，反向依赖会形成包循环。
 * 取值与产品文档 3.1 节的角色表一一对应：
 * 注册用户默认为 PARTICIPANT，只能由 ADMIN 指派为 ORGANIZER。
 * 用字符串存库（与 activity.status 一致），新增角色不会改变历史数据的含义。
 */
public enum UserRole {

    /** 活动参与者：报名、取消报名、查询已发布的活动。 */
    PARTICIPANT,

    /** 活动组织者：创建活动，并管理自己创建的活动。 */
    ORGANIZER,

    /** 系统管理员：查看运行指标、处理异常数据、指派组织者。 */
    ADMIN
}
