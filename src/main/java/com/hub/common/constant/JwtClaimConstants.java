package com.hub.common.constant;

/**
 * JWT 自定义声明
 */
public final class JwtClaimConstants {

    private JwtClaimConstants() {
    }

    /** 令牌类型：用户 */
    public static final String TOKEN_TYPE = "typ";

    public static final String TOKEN_TYPE_USER = "USER";

    public static final String TOKEN_TYPE_ADMIN = "ADMIN";
}
