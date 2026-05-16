package com.hub.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionVo {

    private Long sessionId;
    private Long itemId;
    private Long ownerUserId;
    private Long contactUserId;
    private Long peerUserId;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private Long unreadCount;
}
