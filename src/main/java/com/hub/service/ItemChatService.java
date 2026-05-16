package com.hub.service;

import com.hub.common.PageResult;
import com.hub.domain.dto.request.ChatSendMessageRequest;
import com.hub.domain.vo.ChatMessageVo;
import com.hub.domain.vo.ChatSessionVo;

public interface ItemChatService {

    ChatMessageVo send(long senderUserId, ChatSendMessageRequest request);

    PageResult<ChatSessionVo> sessions(long userId, int page, int size);

    PageResult<ChatMessageVo> messages(long userId, long sessionId, int page, int size);

    void markRead(long userId, long sessionId);

    boolean canAccessSession(long userId, long sessionId);
}
