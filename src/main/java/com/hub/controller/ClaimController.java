package com.hub.controller;

import com.hub.common.PageResult;
import com.hub.common.Result;
import com.hub.domain.dto.request.ClaimCreateRequest;
import com.hub.security.AuthContext;
import com.hub.config.OpenApiConfig;
import com.hub.service.ClaimService;
import com.hub.domain.vo.ClaimVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claim")
@RequiredArgsConstructor
@Validated
@Tag(name = "认领")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
public class ClaimController {

    private final ClaimService claimService;

    @PostMapping
    @Operation(summary = "发起认领")
    public Result<Long> create(@Valid @RequestBody ClaimCreateRequest request) {
        return Result.success(claimService.create(AuthContext.requireUserId(), request));
    }

    @GetMapping("/my")
    @Operation(summary = "我的认领记录（分页）")
    public Result<PageResult<ClaimVo>> my(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(claimService.myClaims(AuthContext.requireUserId(), page, size));
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "认领详情", description = "仅认领人本人可查看")
    public Result<ClaimVo> detail(@Parameter(description = "认领记录 ID") @PathVariable("id") long id) {
        return Result.success(claimService.getById(AuthContext.requireUserId(), id));
    }
}
