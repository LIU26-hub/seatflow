package com.seatflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.seatflow.common.BusinessException;
import com.seatflow.security.AppUser;
import com.seatflow.security.CurrentUser;
import com.seatflow.security.JwtTokenService;
import com.seatflow.user.dto.AuthResponse;
import com.seatflow.user.dto.LoginRequest;
import com.seatflow.user.dto.RegisterRequest;

/**
 * Unit tests for registration and login.
 *
 * <p>中文批注：这里不需要 Spring 上下文和数据库——仓储、编码器、令牌服务都是 mock，
 * 被测对象用构造器直接 new 出来。重点断言三件事：密码绝不能以明文进入仓储、
 * 新用户的角色必须是 PARTICIPANT、失败路径必须返回约定的状态码而不是 500。
 * 另外两条专门守住容易被忽略的安全细节：并发注册必须映射成 409，
 * 用户不存在时也要跑一次密码比较，否则响应时间会泄露用户名是否存在。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "secret123";
    private static final String HASHED_PASSWORD = "$2a$10$hashed-value-for-test";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenService);
    }

    @Test
    void registerHashesPasswordAndCreatesParticipant() {
        given(userRepository.existsByUsername("alice")).willReturn(false);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(HASHED_PASSWORD);
        given(userRepository.save(any(AppUser.class))).willAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        given(jwtTokenService.issue(any(CurrentUser.class))).willReturn("token-value");

        AuthResponse response = authService.register(new RegisterRequest("alice", RAW_PASSWORD, "Alice"));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        then(userRepository).should().save(captor.capture());
        AppUser saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPasswordHash()).isEqualTo(HASHED_PASSWORD);
        assertThat(saved.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
        assertThat(saved.getRole()).isEqualTo(UserRole.PARTICIPANT);
        assertThat(response.token()).isEqualTo("token-value");
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.role()).isEqualTo("PARTICIPANT");
    }

    @Test
    void registerRejectsDuplicateUsernameWithoutWriting() {
        given(userRepository.existsByUsername("alice")).willReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", RAW_PASSWORD, "Alice")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("username already exists")
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(409));

        then(userRepository).should(never()).save(any());
    }

    @Test
    void loginReturnsTokenForCorrectPassword() {
        AppUser user = userWithHash("alice", HASHED_PASSWORD, UserRole.ORGANIZER);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(true);
        given(jwtTokenService.issue(any(CurrentUser.class))).willReturn("token-value");

        AuthResponse response = authService.login(new LoginRequest("alice", RAW_PASSWORD));

        assertThat(response.token()).isEqualTo("token-value");
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo("ORGANIZER");
    }

    @Test
    void loginRejectsWrongPassword() {
        AppUser user = userWithHash("alice", HASHED_PASSWORD, UserRole.PARTICIPANT);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", HASHED_PASSWORD)).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("invalid username or password")
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(401));
    }

    @Test
    void loginRejectsUnknownUserWithTheSameMessage() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("invalid username or password")
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(401));
    }

    @Test
    void loginOfUnknownUserStillRunsPasswordComparison() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class);

        // 关键断言：即使用户不存在，也必须真正调用一次 matches，
        // 否则"查不到就返回"会比"密码错误"快得多，时间差本身就能暴露用户名是否存在。
        then(passwordEncoder).should().matches(eq(RAW_PASSWORD), anyString());
    }

    @Test
    void concurrentDuplicateRegistrationIsMappedToConflict() {
        given(userRepository.existsByUsername("alice")).willReturn(false);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(HASHED_PASSWORD);
        // 用户的 id 使用 IDENTITY 主键策略，Hibernate 必须在 persist 时立刻 INSERT
        // 才能拿到自增 id，所以 save() 在这里是同步打库的，异常能被下面的 catch 接住。
        // 如果以后改成 SEQUENCE/TABLE 或开启批量写入，INSERT 会推迟到事务提交，
        // 那时必须改成 saveAndFlush(...)，否则这个 catch 形同虚设。
        given(userRepository.save(any(AppUser.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", RAW_PASSWORD, "Alice")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(409));
    }

    private AppUser userWithHash(String username, String hash, UserRole role) {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername(username);
        user.setPasswordHash(hash);
        user.setDisplayName(username);
        user.setRole(role);
        return user;
    }
}
