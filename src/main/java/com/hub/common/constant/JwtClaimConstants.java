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

    /** 会话标识（UUID），用于单设备登录互踢 */
    public static final String SESSION_ID = "sid";

    /** Token 版本号，登出/改密后递增使旧 Token 失效 */
    public static final String TOKEN_VERSION = "ver";
}
