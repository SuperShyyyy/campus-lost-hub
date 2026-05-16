package com.hub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_BEARER = "bearerAuth";

    @Bean
    public OpenAPI campusLostHubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CampusLostHub")
                        .description("校园失物招领平台 API。用户接口在「Authorize」中填写 `Bearer <用户登录 token>`；"
                                + "`/api/admin` 下除登录外的接口请填写管理员登录返回的 JWT。")
                        .version("1.0"))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .name(HttpHeaders.AUTHORIZATION)));
    }
}
