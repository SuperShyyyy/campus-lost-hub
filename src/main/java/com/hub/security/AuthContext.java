package com.hub.security;

import com.hub.exception.UnauthorizedException;

/**
 * 当前请求的用户 / 管理员身份（由 JWT 过滤器写入，请求结束清理）
 */
public final class AuthContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> ADMIN_ID = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static void setAdminId(Long adminId) {
        ADMIN_ID.set(adminId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static Long getAdminId() {
        return ADMIN_ID.get();
    }

    public static long requireUserId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new UnauthorizedException("未登录或登录已失效");
        }
        return id;
    }

    public static long requireAdminId() {
        Long id = ADMIN_ID.get();
        if (id == null) {
            throw new UnauthorizedException("管理员未登录或登录已失效");
        }
        return id;
    }

    public static void clear() {
        USER_ID.remove();
        ADMIN_ID.remove();
    }
}
