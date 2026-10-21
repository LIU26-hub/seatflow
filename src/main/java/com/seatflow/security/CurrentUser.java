package com.seatflow.security;

import com.seatflow.user.UserRole;

/**
 * The authenticated caller, stored as the principal of the security context.
 *
 * <p>中文批注：这是"当前登录用户"在代码里的样子，只放鉴权需要的三个字段。
 * 刻意不放密码哈希、邮箱等业务字段：放进 SecurityContext 的东西会在整个请求生命周期里被到处传，
 * 越少越安全。服务层通过 CurrentUserProvider 拿到它，再做角色与归属判断。
 */
public record CurrentUser(Long id, String username, UserRole role) {

    /** 只有组织者能创建活动；管理员不代替组织者建活动，只负责指派与巡检。 */
    public boolean canCreateActivity() {
        return role == UserRole.ORGANIZER;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
