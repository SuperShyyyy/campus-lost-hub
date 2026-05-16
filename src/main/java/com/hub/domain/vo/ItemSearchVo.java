package com.hub.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "物品向量搜索结果")
public class ItemSearchVo extends ItemVo {

    @Schema(description = "匹配度，范围0-1")
    private Double score;

    @Schema(description = "向量距离，值越小越相似")
    private Double distance;
}
