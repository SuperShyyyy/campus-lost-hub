package com.hub.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "发布物品")
public class ItemCreateRequest {

    @NotNull(message = "type不能为空")
    @Min(0)
    @Max(1)
    private Integer type;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100)
    private String title;

    @NotBlank(message = "描述不能为空")
    @Size(max = 2000, message = "描述长度不能超过2000个字符")
    private String description;

    @Size(max = 100)
    private String location;
}
