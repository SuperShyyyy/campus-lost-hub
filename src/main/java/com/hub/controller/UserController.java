package com.hub.controller;

import com.hub.common.Result;
import com.hub.domain.dto.request.ChangePasswordRequest;
import com.hub.domain.dto.request.UserLoginRequest;
import com.hub.domain.dto.request.UserRegisterRequest;
import com.hub.domain.dto.response.TokenResponse;
import com.hub.security.AuthContext;
import com.hub.security.RateLimitService;
import com.hub.service.UserService;
import com.hub.config.OpenApiConfig;
import com.hub.domain.vo.UserMeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Validated
@Tag(name = "用户")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
public class UserController {

    private final UserService userService;
    private final RateLimitService rateLimitService;

    @Value("${lost-hub.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @PostMapping("/register")
    @Operation(summary = "注册")
    @SecurityRequirements
    public Result<Void> register(@Valid @RequestBody UserRegisterRequest request, HttpServletRequest httpRequest) {
        rateLimitService.checkUserRegisterRateLimit(getClientIp(httpRequest));
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

    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "更新资料", description = "可同时更新用户名和头像。头像文件通过 multipart/form-data 上传，先存至 OSS 得到地址后再写入数据库；若数据库更新失败会自动回滚删除 OSS 图片。")
    public Result<Void> update(
            @RequestPart(required = false) MultipartFile avatar,
            @RequestParam(required = false) String username) {
        userService.update(AuthContext.requireUserId(), avatar, username);
        return Result.success();
    }

    @PostMapping("/logout")
    @Operation(summary = "登出", description = "使当前用户所有登录令牌失效")
    public Result<Void> logout() {
        userService.logout(AuthContext.requireUserId());
        return Result.success();
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "修改后所有旧令牌失效，需重新登录")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(AuthContext.requireUserId(), request);
        return Result.success();
    }

    /**
     * 获取客户端真实 IP。
     * 仅在部署于可信反向代理之后时开启 trust-proxy-headers=true，
     * 否则 X-Forwarded-For 可被客户端伪造绕过 IP 限流。
     */
    private String getClientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                return ip.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
