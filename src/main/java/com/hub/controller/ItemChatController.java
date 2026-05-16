package com.hub.controller;

import com.hub.common.PageResult;
import com.hub.common.Result;
import com.hub.config.OpenApiConfig;
import com.hub.domain.dto.request.ChatSendMessageRequest;
import com.hub.domain.vo.ChatMessageVo;
import com.hub.domain.vo.ChatSessionVo;
import com.hub.security.AuthContext;
import com.hub.service.ItemChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Validated
@Tag(name = "消息")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
public class ItemChatController {

    private final ItemChatService itemChatService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/sessions")
    @Operation(summary = "会话列表（分页）")
    public Result<PageResult<ChatSessionVo>> sessions(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(itemChatService.sessions(AuthContext.requireUserId(), page, size));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "会话消息历史（分页）")
    public Result<PageResult<ChatMessageVo>> messages(
            @Parameter(description = "会话 ID") @PathVariable("sessionId") long sessionId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.success(itemChatService.messages(AuthContext.requireUserId(), sessionId, page, size));
    }

    @PostMapping("/send")
    @Operation(summary = "发送消息（REST 兜底）")
    public Result<ChatMessageVo> send(@Valid @RequestBody ChatSendMessageRequest request) {
        ChatMessageVo message = itemChatService.send(AuthContext.requireUserId(), request);
        messagingTemplate.convertAndSend("/topic/chat.session." + message.getSessionId(), message);
        return Result.success(message);
    }

    @PutMapping("/sessions/{sessionId}/read")
    @Operation(summary = "标记会话已读")
    public Result<Void> markRead(@Parameter(description = "会话 ID") @PathVariable("sessionId") long sessionId) {
        itemChatService.markRead(AuthContext.requireUserId(), sessionId);
        return Result.success();
    }
}
