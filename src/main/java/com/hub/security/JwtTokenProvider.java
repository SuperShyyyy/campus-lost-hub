package com.hub.security;

public interface JwtTokenProvider {

    String createUserToken(long userId);

    String createAdminToken(long adminId);

    long parseUserId(String bearerToken);

    long parseAdminId(String bearerToken);
}
