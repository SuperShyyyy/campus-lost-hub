package com.hub.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVo {

    private Long id;
    private Long sessionId;
    private Long itemId;
    private Long senderUserId;
    private Long receiverUserId;
    private String content;
    private Integer msgType;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
