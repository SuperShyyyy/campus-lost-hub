package com.hub.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    @Autowired
    private ChatMemoryStore redisChatMemoryStore;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(){
        ChatMemoryProvider chatMemoryProvider = new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
                return MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(redisChatMemoryStore)//配置ChatMemoryStore
                        .build();
            }
        };
        return chatMemoryProvider;
    }
}