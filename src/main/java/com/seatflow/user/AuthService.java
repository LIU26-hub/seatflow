package com.seatflow.user;

import com.seatflow.common.BusinessException;
import com.seatflow.security.AppUser;
import com.seatflow.security.CurrentUser;
import com.seatflow.security.JwtTokenService;
import com.seatflow.user.dto.AuthResponse;
import com.seatflow.user.dto.LoginRequest;
import com.seatflow.user.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;//密码变成哈希值
    private final JwtTokenService jwtTokenService;

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
        userRepository.save(user);
        CurrentUser currentUser = user.toCurrentUser();
        String token = jwtTokenService.issue(currentUser);
        return AuthResponse.from(token, currentUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(401, "invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "invalid username or password");
        }
        CurrentUser currentUser = user.toCurrentUser();
        String token = jwtTokenService.issue(currentUser);
        return AuthResponse.from(token, currentUser);
    }
}
