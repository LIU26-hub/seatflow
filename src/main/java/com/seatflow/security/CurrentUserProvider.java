package com.seatflow.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.seatflow.common.BusinessException;

/**
 * The single place the rest of the application asks "who is calling?".
 *
 * <p>中文批注：服务层不要自己去翻 SecurityContextHolder，统一从这里拿。
 * 好处有两个：将来身份来源变了（比如换成网关透传的请求头），只需要改这一个类；
 * 单元测试里可以直接构造对象，不需要伪造整个安全上下文。
 * require() 在拿不到身份时抛 401 业务异常，作为"授权规则万一漏配"时的兜底，
 * 正常路径上未登录请求根本进不到服务层。
 */
@Component
public class CurrentUserProvider {

    //从SecurityContextHolder获取当前用户，未登录则返回空Optional。
    public Optional<CurrentUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    //从SecurityContextHolder获取当前用户，如果未登录则抛出异常。
    public CurrentUser require() {
        return find().orElseThrow(() -> new BusinessException(401, "authentication required"));
    }
}
