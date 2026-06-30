package com.hub.security;

import java.time.Duration;

public interface JwtTokenProvider {

    /**
     * 签发用户 JWT，包含 userId、sessionId、tokenVersion。
     */
    String createUserToken(long userId, String sessionId, long tokenVersion);

    /**
     * 签发管理员 JWT。
     */
    String createAdminToken(long adminId);

    /**
     * 解析用户 JWT，返回 userId、sessionId、tokenVersion。
     */
    TokenInfo parseUserToken(String bearerToken);

    /**
     * 解析管理员 JWT，返回 adminId。
     */
    long parseAdminId(String bearerToken);

    /**
     * 获取 JWT 过期时长，用于 ban 标记 / session 的 Redis TTL。
     */
    Duration getTokenExpireDuration();

    /**
     * JWT 解析结果。
     */
    record TokenInfo(long userId, String sessionId, long tokenVersion) {}
}
