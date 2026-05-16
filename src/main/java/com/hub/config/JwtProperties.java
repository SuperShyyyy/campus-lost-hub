package com.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * 用户 JWT 签名密钥（HS256，建议环境变量覆盖）
     */
    private String userSecret = "";

    /**
     * 管理员 JWT 签名密钥（与用户分离）
     */
    private String adminSecret = "";

    /**
     * 过期小时数
     */
    private long expireHours = 24;
}
