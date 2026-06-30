package com.hub.domain.repository;

import com.hub.config.AiMemoryProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Repository
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final AiMemoryProperties aiMemoryProperties;

    public RedisChatMemoryStore(StringRedisTemplate stringRedisTemplate, AiMemoryProperties aiMemoryProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.aiMemoryProperties = aiMemoryProperties;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        if (memoryId == null) {
            return Collections.emptyList();
        }
        String json = stringRedisTemplate.opsForValue().get(memoryId.toString());
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        String json = ChatMessageSerializer.messagesToJson(list);
        Duration ttl = aiMemoryProperties.getTtl();
        stringRedisTemplate.opsForValue().set(memoryId.toString(), json, ttl);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(memoryId.toString());
    }

    public boolean hasMemory(Object memoryId) {
        return memoryId != null && Boolean.TRUE.equals(stringRedisTemplate.hasKey(memoryId.toString()));
    }

    public String buildMemoryKey(Long userId, Long sessionId) {
        return "chat:user:" + userId + ":session:" + sessionId;
    }
}
