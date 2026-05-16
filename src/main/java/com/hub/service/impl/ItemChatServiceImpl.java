package com.hub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hub.common.PageResult;
import com.hub.common.constant.ChatConstants;
import com.hub.domain.dto.request.ChatSendMessageRequest;
import com.hub.domain.po.Item;
import com.hub.domain.po.ItemChatMessage;
import com.hub.domain.po.ItemChatSession;
import com.hub.domain.vo.ChatMessageVo;
import com.hub.domain.vo.ChatSessionVo;
import com.hub.exception.ForbiddenException;
import com.hub.exception.NotFoundException;
import com.hub.mapper.ItemChatMessageMapper;
import com.hub.mapper.ItemChatSessionMapper;
import com.hub.mapper.ItemMapper;
import com.hub.mapper.UserMapper;
import com.hub.security.SensitiveContentGuard;
import com.hub.service.ItemChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemChatServiceImpl implements ItemChatService {

    private final ItemMapper itemMapper;
    private final UserMapper userMapper;
    private final ItemChatSessionMapper sessionMapper;
    private final ItemChatMessageMapper messageMapper;
    private final SensitiveContentGuard sensitiveContentGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVo send(long senderUserId, ChatSendMessageRequest request) {
        Item item = itemMapper.selectById(request.getItemId());
        if (item == null) {
            throw new NotFoundException("物品不存在");
        }

        long ownerUserId = item.getUserId();
        long receiverUserId = request.getReceiverUserId();
        if (senderUserId == receiverUserId) {
            throw new IllegalArgumentException("不能给自己发送消息");
        }

        if (userMapper.selectById(receiverUserId) == null) {
            throw new NotFoundException("接收者不存在");
        }

        long contactUserId;
        if (senderUserId == ownerUserId) {
            contactUserId = receiverUserId;
            if (contactUserId == ownerUserId) {
                throw new IllegalArgumentException("发布者不能给自己发送消息");
            }
        } else {
            if (receiverUserId != ownerUserId) {
                throw new ForbiddenException("只能给该物品发布者发送消息");
            }
            contactUserId = senderUserId;
        }

        String content = sensitiveContentGuard.normalizeAndValidate(request.getContent());
        ItemChatSession session = getOrCreateSession(item.getId(), ownerUserId, contactUserId);

        if (!isParticipant(senderUserId, session)) {
            throw new ForbiddenException("仅会话参与者可发送消息");
        }
        if (!isCounterparty(senderUserId, receiverUserId, session)) {
            throw new ForbiddenException("接收方不在当前会话中");
        }

        LocalDateTime now = LocalDateTime.now();
        ItemChatMessage msg = new ItemChatMessage();
        msg.setSessionId(session.getId());
        msg.setItemId(item.getId());
        msg.setSenderUserId(senderUserId);
        msg.setReceiverUserId(receiverUserId);
        msg.setContent(content);
        msg.setMsgType(ChatConstants.MESSAGE_TYPE_TEXT);
        msg.setIsRead(false);
        msg.setCreatedAt(now);
        messageMapper.insert(msg);

        ItemChatSession update = new ItemChatSession();
        update.setId(session.getId());
        update.setLastMessageAt(now);
        update.setLastMessagePreview(preview(content));
        update.setUpdatedAt(now);
        sessionMapper.updateById(update);

        return toMessageVo(msg);
    }

    @Override
    public PageResult<ChatSessionVo> sessions(long userId, int page, int size) {
        LambdaQueryWrapper<ItemChatSession> wrapper = new LambdaQueryWrapper<ItemChatSession>()
                .and(w -> w.eq(ItemChatSession::getOwnerUserId, userId).or().eq(ItemChatSession::getContactUserId, userId))
                .orderByDesc(ItemChatSession::getLastMessageAt)
                .orderByDesc(ItemChatSession::getId);
        Page<ItemChatSession> p = new Page<>(page, size);
        sessionMapper.selectPage(p, wrapper);
        List<ChatSessionVo> records = p.getRecords().stream().map(s -> toSessionVo(s, userId)).toList();
        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public PageResult<ChatMessageVo> messages(long userId, long sessionId, int page, int size) {
        assertParticipant(userId, sessionId);
        LambdaQueryWrapper<ItemChatMessage> wrapper = new LambdaQueryWrapper<ItemChatMessage>()
                .eq(ItemChatMessage::getSessionId, sessionId)
                .orderByDesc(ItemChatMessage::getCreatedAt)
                .orderByDesc(ItemChatMessage::getId);
        Page<ItemChatMessage> p = new Page<>(page, size);
        messageMapper.selectPage(p, wrapper);
        return new PageResult<>(
                p.getRecords().stream().map(this::toMessageVo).toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(long userId, long sessionId) {
        assertParticipant(userId, sessionId);
        LambdaUpdateWrapper<ItemChatMessage> wrapper = new LambdaUpdateWrapper<ItemChatMessage>()
                .eq(ItemChatMessage::getSessionId, sessionId)
                .eq(ItemChatMessage::getReceiverUserId, userId)
                .eq(ItemChatMessage::getIsRead, false)
                .set(ItemChatMessage::getIsRead, true);
        messageMapper.update(null, wrapper);
    }

    @Override
    public boolean canAccessSession(long userId, long sessionId) {
        ItemChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return false;
        }
        return isParticipant(userId, session);
    }

    private ItemChatSession getOrCreateSession(long itemId, long ownerUserId, long contactUserId) {
        LambdaQueryWrapper<ItemChatSession> query = new LambdaQueryWrapper<ItemChatSession>()
                .eq(ItemChatSession::getItemId, itemId)
                .eq(ItemChatSession::getOwnerUserId, ownerUserId)
                .eq(ItemChatSession::getContactUserId, contactUserId)
                .last("limit 1");
        ItemChatSession existing = sessionMapper.selectOne(query);
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        ItemChatSession session = new ItemChatSession();
        session.setItemId(itemId);
        session.setOwnerUserId(ownerUserId);
        session.setContactUserId(contactUserId);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        try {
            sessionMapper.insert(session);
            return session;
        } catch (DuplicateKeyException e) {
            ItemChatSession concurrent = sessionMapper.selectOne(query);
            if (concurrent == null) {
                throw e;
            }
            return concurrent;
        }
    }

    private ItemChatSession assertParticipant(long userId, long sessionId) {
        ItemChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        if (!isParticipant(userId, session)) {
            throw new ForbiddenException("无权访问该会话");
        }
        return session;
    }

    private boolean isParticipant(long userId, ItemChatSession session) {
        return userId == session.getOwnerUserId() || userId == session.getContactUserId();
    }

    private boolean isCounterparty(long senderUserId, long receiverUserId, ItemChatSession session) {
        if (senderUserId == session.getOwnerUserId()) {
            return receiverUserId == session.getContactUserId();
        }
        if (senderUserId == session.getContactUserId()) {
            return receiverUserId == session.getOwnerUserId();
        }
        return false;
    }

    private ChatMessageVo toMessageVo(ItemChatMessage msg) {
        ChatMessageVo vo = new ChatMessageVo();
        vo.setId(msg.getId());
        vo.setSessionId(msg.getSessionId());
        vo.setItemId(msg.getItemId());
        vo.setSenderUserId(msg.getSenderUserId());
        vo.setReceiverUserId(msg.getReceiverUserId());
        vo.setContent(msg.getContent());
        vo.setMsgType(msg.getMsgType());
        vo.setIsRead(msg.getIsRead());
        vo.setCreatedAt(msg.getCreatedAt());
        return vo;
    }

    private ChatSessionVo toSessionVo(ItemChatSession session, long currentUserId) {
        ChatSessionVo vo = new ChatSessionVo();
        vo.setSessionId(session.getId());
        vo.setItemId(session.getItemId());
        vo.setOwnerUserId(session.getOwnerUserId());
        vo.setContactUserId(session.getContactUserId());
        vo.setPeerUserId(currentUserId == session.getOwnerUserId() ? session.getContactUserId() : session.getOwnerUserId());
        vo.setLastMessagePreview(session.getLastMessagePreview());
        vo.setLastMessageAt(session.getLastMessageAt());
        vo.setUnreadCount(unreadCount(currentUserId, session.getId()));
        return vo;
    }

    private long unreadCount(long userId, long sessionId) {
        LambdaQueryWrapper<ItemChatMessage> wrapper = new LambdaQueryWrapper<ItemChatMessage>()
                .eq(ItemChatMessage::getSessionId, sessionId)
                .eq(ItemChatMessage::getReceiverUserId, userId)
                .eq(ItemChatMessage::getIsRead, false);
        return messageMapper.selectCount(wrapper);
    }

    private String preview(String content) {
        if (content.length() <= ChatConstants.MESSAGE_PREVIEW_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, ChatConstants.MESSAGE_PREVIEW_MAX_LENGTH);
    }
}
