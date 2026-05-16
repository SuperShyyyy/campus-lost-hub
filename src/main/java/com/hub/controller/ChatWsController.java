package com.hub.controller;

import com.hub.domain.dto.request.ChatSendMessageRequest;
import com.hub.domain.vo.ChatMessageVo;
import com.hub.exception.UnauthorizedException;
import com.hub.service.ItemChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;

@Controller
@Validated
@RequiredArgsConstructor
public class ChatWsController {

    private final ItemChatService itemChatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void send(@Valid @Payload ChatSendMessageRequest request, Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("未登录或登录已失效");
        }
        long senderUserId = Long.parseLong(principal.getName());
        ChatMessageVo message = itemChatService.send(senderUserId, request);
        messagingTemplate.convertAndSend("/topic/chat.session." + message.getSessionId(), message);
    }
}
