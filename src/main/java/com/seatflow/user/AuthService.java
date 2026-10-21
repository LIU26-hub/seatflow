package com.seatflow.user;

import java.util.Optional;

import com.seatflow.common.BusinessException;
import com.seatflow.security.AppUser;
import com.seatflow.security.CurrentUser;
import com.seatflow.security.JwtTokenService;
import com.seatflow.user.dto.AuthResponse;
import com.seatflow.user.dto.LoginRequest;
import com.seatflow.user.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;//密码变成哈希值
    private final JwtTokenService jwtTokenService;

    /**
     * A well-formed BCrypt hash that no real account uses.
     *
     * <p>中文批注：专供"用户名不存在"这条分支做一次真实比较，用来消除时差。
     * 必须是格式合法的 BCrypt 串：BCryptPasswordEncoder 遇到格式非法的串会直接返回 false，
     * 那样就起不到对齐耗时的作用。
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$DHFXZ0q9fuhYEGY8lsEduujaQufiExm6SkErEGa5DuDjTscyA3qW2";

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw BusinessException.conflict("username already exists: " + request.username());
        }
        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        // role 默认 PARTICIPANT，无需显式设置
        //防止两个请求同时通过 existsByUsername，然后都去 insert，其中一个撞唯一索引变成 500
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("username already exists: " + request.username());
        }
        CurrentUser currentUser = user.toCurrentUser();
        String token = jwtTokenService.issue(currentUser);
        return AuthResponse.from(token, currentUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<AppUser> found = userRepository.findByUsername(request.username());
        if (found.isEmpty()) {
            // 时差消除：用户不存在时也跑一次真实的 BCrypt 比较。
            // 否则未知用户名走"查库后立刻返回"的短路径，而密码错误要走完整的 BCrypt 哈希计算，
            // 两者的响应时间差足以被用来枚举哪些用户名已经注册。
            // 这里刻意丢弃返回值，只为了消耗与真实校验相同的 CPU 时间。
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new BusinessException(401, "invalid username or password");
        }
        AppUser user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "invalid username or password");
        }
        CurrentUser currentUser = user.toCurrentUser();
        String token = jwtTokenService.issue(currentUser);
        return AuthResponse.from(token, currentUser);
    }
}
