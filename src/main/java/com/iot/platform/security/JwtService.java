package com.iot.platform.security;

import com.iot.platform.config.IotProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验。HS256，密钥来自 iot.jwt.secret（生产环境务必用环境变量覆盖）。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expireMinutes;

    public JwtService(IotProperties props) {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("iot.jwt.secret 至少需要 32 字节，否则 HS256 无法签名");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = props.getJwt().getExpireMinutes();
    }

    public String generate(LoginUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.username())
                .claim("uid", user.id())
                .claim("role", user.role())
                .claim("nickname", user.nickname())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireMinutes * 60)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，失败返回 null（由过滤器决定放行还是拒绝）。
     */
    public LoginUser parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return new LoginUser(
                    c.get("uid", Number.class).longValue(),
                    c.getSubject(),
                    c.get("nickname", String.class),
                    c.get("role", String.class));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }
}
