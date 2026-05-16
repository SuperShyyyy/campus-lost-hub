package com.hub.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "发起认领")
public class ClaimCreateRequest {

    @NotNull(message = "itemId不能为空")
    private Long itemId;

    @NotBlank(message = "留言不能为空")
    @Size(max = 255)
    private String message;
}
