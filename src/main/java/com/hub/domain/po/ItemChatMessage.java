package com.hub.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("item_chat_message")
public class ItemChatMessage {

    @TableId(type = IdType.AUTO)
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
