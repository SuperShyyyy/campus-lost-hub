package com.hub.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "审核认领")
public class ClaimAuditRequest {

    /** 1 通过 2 拒绝 */
    @NotNull(message = "status不能为空")
    @Min(1)
    @Max(2)
    @Schema(description = "1 通过，2 拒绝", allowableValues = {"1", "2"})
    private Integer status;
}
