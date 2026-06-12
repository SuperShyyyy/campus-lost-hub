package com.hub.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新用户资料")
public class UserUpdateRequest {

    @Size(max = 50, message = "用户名过长")
    private String username;

    @Size(max = 255, message = "头像地址过长")
    @Pattern(regexp = "^https?://.*", message = "头像地址须为合法 HTTP(S) URL")
    private String avatar;
}
