package com.hub.controller;

import com.hub.common.Result;
import com.hub.domain.dto.request.UserLoginRequest;
import com.hub.domain.dto.request.UserRegisterRequest;
import com.hub.domain.dto.request.UserUpdateRequest;
import com.hub.domain.dto.response.TokenResponse;
import com.hub.security.AuthContext;
import com.hub.service.UserService;
import com.hub.config.OpenApiConfig;
import com.hub.domain.vo.UserMeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Validated
@Tag(name = "用户")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "注册")
    @SecurityRequirements
    public Result<Void> register(@Valid @RequestBody UserRegisterRequest request) {
        userService.register(request);
        return Result.success();
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "返回 JWT，后续请求在 Header 中携带：Authorization: Bearer {token}")
    @SecurityRequirements
    public Result<TokenResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return Result.success(userService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "当前用户信息")
    public Result<UserMeVo> me() {
        return Result.success(userService.getMe(AuthContext.requireUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新资料")
    public Result<Void> update(@Valid @RequestBody UserUpdateRequest request) {
        userService.update(AuthContext.requireUserId(), request);
        return Result.success();
    }
}
