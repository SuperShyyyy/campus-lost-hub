package com.hub.controller;

import com.hub.common.PageResult;
import com.hub.common.Result;
import com.hub.domain.dto.request.AdminLoginRequest;
import com.hub.domain.dto.request.ClaimAuditRequest;
import com.hub.domain.dto.response.TokenResponse;
import com.hub.config.OpenApiConfig;
import com.hub.service.AdminService;
import com.hub.domain.vo.ClaimVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@Tag(name = "管理员")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/login")
    @Operation(summary = "管理员登录", description = "返回管理员 JWT，用于审核认领、封禁用户等接口")
    @SecurityRequirements
    public Result<TokenResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(adminService.login(request));
    }

    @GetMapping("/claims")
    @Operation(summary = "认领审核列表（分页）")
    public Result<PageResult<ClaimVo>> claims(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(adminService.claimsPage(page, size));
    }

    @PutMapping("/claim/audit/{id:\\d+}")
    @Operation(summary = "审核认领", description = "status：1 通过，2 拒绝")
    public Result<Void> audit(
            @Parameter(description = "认领记录 ID") @PathVariable("id") long id,
            @Valid @RequestBody ClaimAuditRequest request) {
        adminService.auditClaim(id, request);
        return Result.success();
    }

    @PutMapping("/user/ban/{userId:\\d+}")
    @Operation(summary = "封禁用户")
    public Result<Void> ban(@Parameter(description = "用户 ID") @PathVariable("userId") long userId) {
        adminService.banUser(userId);
        return Result.success();
    }
}
