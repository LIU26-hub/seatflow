package com.seatflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import com.seatflow.user.UserRole;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for token issuing and verification.
 *
 * <p>中文批注：这类测试的价值在于"过期"可以被确定性地验证——靠给服务注入不同的固定时钟，
 * 而不是让测试真的等两小时。用例覆盖：正常往返、过期被拒、临近过期仍然有效、换密钥签发被拒。
 * 最后补一条垃圾串：真实世界里客户端一定会塞进各种奇怪内容，解析必须安静失败而不是抛异常。
 */
class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret-long-enough-for-hs256-0123456789";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant ISSUED_AT = Instant.parse("2026-11-03T04:00:00Z");
    private static final Duration TTL = Duration.ofHours(2);
    private static final CurrentUser USER = new CurrentUser(42L, "liuminghui", UserRole.ORGANIZER);

    private JwtTokenService serviceAt(Instant instant) {
        return new JwtTokenService(SECRET, TTL, Clock.fixed(instant, ZONE));
    }

    @Test
    void tokenRoundTripsToTheSameUser() {
        String token = serviceAt(ISSUED_AT).issue(USER);

        Optional<CurrentUser> parsed = serviceAt(ISSUED_AT.plusSeconds(60)).parse(token);

        assertThat(parsed).contains(USER);
    }

    @Test
    void tokenIsRejectedAfterItsTimeToLive() {
        String token = serviceAt(ISSUED_AT).issue(USER);

        assertThat(serviceAt(ISSUED_AT.plus(TTL).plusSeconds(1)).parse(token)).isEmpty();
    }

    @Test
    void tokenIsStillAcceptedJustBeforeExpiry() {
        String token = serviceAt(ISSUED_AT).issue(USER);

        assertThat(serviceAt(ISSUED_AT.plus(TTL).minusSeconds(1)).parse(token)).contains(USER);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = serviceAt(ISSUED_AT).issue(USER);
        JwtTokenService otherService = new JwtTokenService(
                "a-completely-different-secret-0123456789abcdef", TTL, Clock.fixed(ISSUED_AT, ZONE));

        assertThat(otherService.parse(token)).isEmpty();
    }

    @Test
    void garbageTokenIsRejectedWithoutThrowing() {
        assertThat(serviceAt(ISSUED_AT).parse("not-a-jwt")).isEmpty();
        assertThat(serviceAt(ISSUED_AT).parse("")).isEmpty();
    }
}
