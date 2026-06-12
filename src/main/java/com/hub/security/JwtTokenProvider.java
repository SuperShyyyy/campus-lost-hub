package com.hub.security;

import java.time.Duration;

public interface JwtTokenProvider {

    String createUserToken(long userId);

    String createAdminToken(long adminId);

    long parseUserId(String bearerToken);

    long parseAdminId(String bearerToken);

    /**
     * 获取 JWT 过期时长，用于 ban 标记的 Redis TTL
     */
    Duration getTokenExpireDuration();
}
