package com.hub.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录令牌")
public class TokenResponse {

    @Schema(description = "JWT，请求头：Authorization: Bearer {token}")
    private String token;
}
