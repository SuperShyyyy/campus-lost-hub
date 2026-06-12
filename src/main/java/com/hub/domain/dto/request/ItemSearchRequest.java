package com.hub.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "物品向量搜索请求")
public class ItemSearchRequest {

    @NotBlank(message = "搜索内容不能为空")
    @Size(max = 500, message = "搜索内容不能超过500个字符")
    @Schema(description = "搜索描述，支持自然语言输入", example = "昨天下午在图书馆三楼丢了一个黑色书包")
    private String query;

    @Min(value = 1, message = "页码最小为1")
    @Max(value = 2, message = "最多只支持前2页")
    @Schema(description = "页码，最多支持前2页", example = "1", defaultValue = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 10, message = "每页条数最大为10")
    @Schema(description = "每页条数，最大10", example = "10", defaultValue = "10")
    private Integer size = 10;

    @DecimalMin(value = "0.5", message = "匹配阈值不能低于0.5")
    @DecimalMax(value = "0.8", message = "匹配阈值不能高于0.8")
    @Schema(description = "最小匹配度阈值，范围0.5-0.8", example = "0.6", defaultValue = "0.6")
    private Double minScore = 0.6D;
}
