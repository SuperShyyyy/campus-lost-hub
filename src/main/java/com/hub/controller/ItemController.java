package com.hub.controller;

import com.hub.common.PageResult;
import com.hub.common.Result;
import com.hub.domain.dto.request.ItemCreateRequest;
import com.hub.domain.dto.request.ItemSearchRequest;
import com.hub.security.AuthContext;
import com.hub.config.OpenApiConfig;
import com.hub.service.ImageSearchService;
import com.hub.service.ItemService;
import com.hub.service.LostItemSearchService;
import com.hub.domain.vo.ItemSearchVo;
import com.hub.domain.vo.ItemVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/item")
@RequiredArgsConstructor
@Validated
@Tag(name = "物品")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
public class ItemController {

    private final ItemService itemService;
    private final LostItemSearchService lostItemSearchService;
    private final ImageSearchService imageSearchService;

    @PostMapping
    @Operation(summary = "发布物品")
    public Result<Long> create(@Valid @RequestBody ItemCreateRequest request) {
        return Result.success(itemService.create(AuthContext.requireUserId(), request));
    }

    @PutMapping("/{id:\\d+}")
    @Operation(summary = "更新已发布物品", description = "与发布时字段一致，会同步更新向量检索；仅发布者可操作")
    public Result<Void> update(
            @Parameter(description = "物品 ID") @PathVariable("id") long id,
            @Valid @RequestBody ItemCreateRequest request) {
        itemService.update(AuthContext.requireUserId(), id, request);
        return Result.success();
    }

    @PostMapping("/{id:\\d+}/image")
    @Operation(summary = "上传物品图片", description = "发布成功后上传单张图片，仅发布者可操作")
    public Result<String> uploadImage(
            @Parameter(description = "物品 ID") @PathVariable("id") long id,
            @RequestParam("file") MultipartFile file) {
        return Result.success(itemService.uploadImage(AuthContext.requireUserId(), id, file));
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "删除物品", description = "仅发布者可删除")
    public Result<Void> delete(@Parameter(description = "物品 ID") @PathVariable("id") long id) {
        itemService.delete(AuthContext.requireUserId(), id);
        return Result.success();
    }

    @PutMapping("/{id:\\d+}/complete")
    @Operation(summary = "完结物品", description = "仅发布者可操作，仅支持已匹配物品完结")
    public Result<Void> complete(@Parameter(description = "物品 ID") @PathVariable("id") long id) {
        itemService.complete(AuthContext.requireUserId(), id);
        return Result.success();
    }

    @GetMapping("/list")
    @Operation(summary = "物品列表（分页）", description = "无需登录")
    @SecurityRequirements
    public Result<PageResult<ItemVo>> list(
            @Parameter(description = "类型：0 丢失，1 拾到") @RequestParam(required = false) @Min(0) @Max(1) Integer type,
            @Parameter(description = "状态筛选：0未匹配 1已匹配 2已完结") @RequestParam(required = false) @Min(0) @Max(2) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(itemService.list(type, status, page, size));
    }

    @GetMapping("/my")
    @Operation(summary = "我的发布（分页）")
    public Result<PageResult<ItemVo>> my(
            @Parameter(description = "类型：0 丢失，1 拾到") @RequestParam(required = false) @Min(0) @Max(1) Integer type,
            @Parameter(description = "状态筛选：0未匹配 1已匹配 2已完结") @RequestParam(required = false) @Min(0) @Max(2) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(itemService.myItems(AuthContext.requireUserId(), type, status, page, size));
    }

    @PostMapping("/search")
    @Operation(summary = "物品文本向量搜索", description = "需登录，基于 text_embedding 余弦相似度，最多返回前2页共20条结果")
    public Result<PageResult<ItemSearchVo>> search(@Valid @RequestBody ItemSearchRequest request) {
        return Result.success(lostItemSearchService.search(request));
    }

    @PostMapping("/search/image")
    @Operation(summary = "物品图片向量搜索", description = "需登录；上传图片，按 image_embedding(512维/CLIP) 余弦相似度分页；需启用 lost-hub.clip.enabled")
    public Result<PageResult<ItemSearchVo>> searchByImage(
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "页码，最多支持前2页") @RequestParam(defaultValue = "1") @Min(1) @Max(2) int page,
            @Parameter(description = "每页条数，最大10") @RequestParam(defaultValue = "10") @Min(1) @Max(10) int size,
            @Parameter(description = "最小匹配度阈值，范围0.5-0.8") @RequestParam(required = false) @DecimalMin("0.5") @DecimalMax("0.8") Double minScore) {
        return Result.success(imageSearchService.search(file, page, size, minScore));
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "物品详情", description = "无需登录")
    @SecurityRequirements
    public Result<ItemVo> detail(@Parameter(description = "物品 ID") @PathVariable("id") long id) {
        return Result.success(itemService.getById(id));
    }
}
