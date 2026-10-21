package com.seatflow.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import com.seatflow.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies access tokens.
 *
 * <p>中文批注：令牌里只放三样东西——用户 id（放在标准主题字段 sub）、用户名、角色。
 * 不放密码、不放权限明细：令牌是客户端可见的，任何放进去的内容都等于公开。
 * 签发与校验都走注入的 Clock，因此"两小时后过期"这件事在测试里可以被钉死，
 * 不需要真的等两小时，也不会因为机器时间不同而出现偶发失败。
 * 注意两处时钟都要给：签发时用来算过期时刻，"校验时"也要传给解析器——
 * jjwt 默认拿系统时钟判断 exp 是否已过，不给它时钟的话，测试里固定住的时间对校验不起作用，
 * 过期用例会时对时错。生产环境两者指向同一时刻，所以行为不变；差别只在测试里体现。
 * 解析失败（签名不对、过期、格式错误）统一返回空 Optional，由调用方决定怎么处理——
 * 过滤器选择"当作未登录"，而不是抛异常打断请求。
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    /** 与 application.yml 里的开发默认值保持一致，用于启动时提醒。 */
    private static final String DEVELOPMENT_PLACEHOLDER =
            "dev-only-secret-please-override-in-production-0123456789";

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final Duration ttl;
    private final Clock clock;

    public JwtTokenService(@Value("${seatflow.security.jwt.secret}") String secret,
                           @Value("${seatflow.security.jwt.ttl}") Duration ttl,
                           Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.clock = clock;
        if (DEVELOPMENT_PLACEHOLDER.equals(secret)) {
            log.warn("JWT secret is still the development placeholder; override JWT_SECRET before any real use");
        }
    }

    public String issue(CurrentUser user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim(CLAIM_USERNAME, user.username())
                .claim(CLAIM_ROLE, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    public Optional<CurrentUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new CurrentUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    UserRole.valueOf(claims.get(CLAIM_ROLE, String.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            // 正常情况：签名不符、已过期、或客户端塞了垃圾串。
            // 用 debug 而不是 warn，否则任何人都能靠刷垃圾令牌把日志写满。
            log.debug("rejected access token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** 暴露给测试与调试，避免调用方自己猜配置值。 */
    public Duration ttl() {
        return ttl;
    }
}
