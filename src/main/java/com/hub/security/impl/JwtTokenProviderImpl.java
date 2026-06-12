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
    public String createUserToken(long userId) {
        return buildToken(userId, userKey, JwtClaimConstants.TOKEN_TYPE_USER);
    }

    @Override
    public String createAdminToken(long adminId) {
        return buildToken(adminId, adminKey, JwtClaimConstants.TOKEN_TYPE_ADMIN);
    }

    private String buildToken(long subjectId, SecretKey key, String typ) {
        long now = System.currentTimeMillis();
        long expMs = now + jwtProperties.getExpireHours() * 3600_000L;
        return Jwts.builder()
                .subject(String.valueOf(subjectId))
                .claim(JwtClaimConstants.TOKEN_TYPE, typ)
                .issuedAt(new Date(now))
                .expiration(new Date(expMs))
                .signWith(key)
                .compact();
    }

    @Override
    public long parseUserId(String bearerToken) {
        return parseSubject(bearerToken, userKey, JwtClaimConstants.TOKEN_TYPE_USER);
    }

    @Override
    public long parseAdminId(String bearerToken) {
        return parseSubject(bearerToken, adminKey, JwtClaimConstants.TOKEN_TYPE_ADMIN);
    }

    @Override
    public Duration getTokenExpireDuration() {
        return Duration.ofHours(jwtProperties.getExpireHours());
    }

    private long parseSubject(String bearerToken, SecretKey key, String expectedTyp) {
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
            return Long.parseLong(claims.getSubject());
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        } catch (MalformedJwtException | SignatureException | IllegalArgumentException e) {
            throw new UnauthorizedException("无效的访问令牌");
        }
    }
}
