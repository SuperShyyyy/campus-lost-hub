package com.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI 对话记忆轻量配置。
 * 热数据纯 Redis 存储，不落地数据库，默认 30 分钟过期、保留 5 轮（10 条）消息。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lost-hub.ai.memory")
public class AiMemoryProperties {

    /**
     * Redis 过期时间，默认 30 分钟。
     */
    private Duration ttl = Duration.ofMinutes(30);

    /**
     * 消息窗口最大保留条数（用户+AI 各算 1 条）。
     * 10 条 ≈ 5 轮对话，可按需求调整。
     */
    private int maxMessages = 10;
}
