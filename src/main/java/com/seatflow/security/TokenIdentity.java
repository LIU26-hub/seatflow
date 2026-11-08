package com.seatflow.security;

/**
 * What a valid access token proves: who the caller is, nothing more.
 *
 * <p>中文批注：令牌只证明身份。角色这类"会变"的授权信息不再放进令牌，
 * 否则管理员改了角色，用户还得等到令牌过期（最长两小时）才生效。
 * 解析出身份之后，角色由 UserRoleResolver 每次从 Redis 或数据库取最新的。
 */
public record TokenIdentity(Long id, String username) {
}
