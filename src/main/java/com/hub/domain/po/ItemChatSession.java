package com.hub.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("item_chat_session")
public class ItemChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private Long ownerUserId;

    private Long contactUserId;

    private LocalDateTime lastMessageAt;

    private String lastMessagePreview;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
