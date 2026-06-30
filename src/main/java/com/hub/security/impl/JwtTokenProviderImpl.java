package com.hub.security.impl;

import com.hub.common.constant.JwtClaimConstants;
import com.hub.config.JwtProperties;
import com.hub.exception.UnauthorizedException;
import com.hub.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtTokenProviderImpl implements JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private SecretKey userKey;
    private SecretKey adminKey;

    @PostConstruct
    void initKeys() {
        this.userKey = Keys.hmacShaKeyFor(jwtProperties.getUserSecret().getBytes(StandardCharsets.UTF_8));
        this.adminKey = Keys.hmacShaKeyFor(jwtProperties.getAdminSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String createUserToken(long userId, String sessionId, long tokenVersion) {
        long now = System.currentTimeMillis();
        long expMs = now + jwtProperties.getExpireHours() * 3600_000L;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(JwtClaimConstants.TOKEN_TYPE, JwtClaimConstants.TOKEN_TYPE_USER)
                .claim(JwtClaimConstants.SESSION_ID, sessionId)
                .claim(JwtClaimConstants.TOKEN_VERSION, tokenVersion)
                .issuedAt(new Date(now))
                .expiration(new Date(expMs))
                .signWith(userKey)
                .compact();
    }

    @Override
    public String createAdminToken(long adminId) {
        long now = System.currentTimeMillis();
        long expMs = now + jwtProperties.getExpireHours() * 3600_000L;
        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim(JwtClaimConstants.TOKEN_TYPE, JwtClaimConstants.TOKEN_TYPE_ADMIN)
                .issuedAt(new Date(now))
                .expiration(new Date(expMs))
                .signWith(adminKey)
                .compact();
    }

    @Override
    public TokenInfo parseUserToken(String bearerToken) {
        Claims claims = parseJwt(bearerToken, userKey, JwtClaimConstants.TOKEN_TYPE_USER);
        long userId = Long.parseLong(claims.getSubject());
        String sessionId = claims.get(JwtClaimConstants.SESSION_ID, String.class);
        if (sessionId == null || sessionId.isBlank()) {
            throw new UnauthorizedException("令牌缺少会话标识");
        }
        Long ver = claims.get(JwtClaimConstants.TOKEN_VERSION, Long.class);
        long tokenVersion = ver != null ? ver : 0L;
        return new TokenInfo(userId, sessionId, tokenVersion);
    }

    @Override
    public long parseAdminId(String bearerToken) {
        Claims claims = parseJwt(bearerToken, adminKey, JwtClaimConstants.TOKEN_TYPE_ADMIN);
        return Long.parseLong(claims.getSubject());
    }

    @Override
    public Duration getTokenExpireDuration() {
        return Duration.ofHours(jwtProperties.getExpireHours());
    }

    /**
     * 解析并校验 JWT 签名、类型和过期时间，返回 Claims。
     */
    private Claims parseJwt(String bearerToken, SecretKey key, String expectedTyp) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new UnauthorizedException("缺少访问令牌");
        }
        String raw = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken.trim();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(raw)
                    .getPayload();
            String typ = claims.get(JwtClaimConstants.TOKEN_TYPE, String.class);
            if (!expectedTyp.equals(typ)) {
                throw new UnauthorizedException("令牌类型无效");
            }
            return claims;
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        } catch (MalformedJwtException | SignatureException | IllegalArgumentException e) {
            throw new UnauthorizedException("无效的访问令牌");
        }
    }
}
