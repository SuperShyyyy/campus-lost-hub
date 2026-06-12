package com.hub.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "发送私聊消息")
public class ChatSendMessageRequest {

    @NotNull(message = "itemId不能为空")
    private Long itemId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 1000, message = "消息内容最多1000字符")
    private String content;
}
