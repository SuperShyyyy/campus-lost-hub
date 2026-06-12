package com.hub.security;

import com.hub.common.constant.RedisKeyConstants;
import com.hub.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 Redis SETNX 的用户请求冷却限流。
 * <p>
 * 文本搜索：每 15s 限 1 次（key: rate:textEmbedding:userId）
 * 图片搜索：每 30s 限 1 次（key: rate:imageEmbedding:userId）
 * 物品发布：每 10s 限 1 次（key: rate:itemCreate:userId）
 * 聊天发送：每 3s 限 1 次（key: rate:chatSend:userId）
 * 用户注册：每 60s 限 1 次（key: rate:userRegister:ip）
 * 认领提交：每 10s 限 1 次（key: rate:claimCreate:userId）
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final Duration TEXT_SEARCH_COOLDOWN = Duration.ofSeconds(15);
    private static final Duration IMAGE_SEARCH_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration ITEM_CREATE_COOLDOWN = Duration.ofSeconds(10);
    private static final Duration CHAT_SEND_COOLDOWN = Duration.ofSeconds(3);
    private static final Duration USER_REGISTER_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration CLAIM_CREATE_COOLDOWN = Duration.ofSeconds(10);

    /**
     * 检查文本搜索限流。未超限时通过 SETNX 原子设置冷却标记，超限时抛出 RateLimitException。
     */
    public void checkTextSearchRateLimit(long userId) {
        String key = RedisKeyConstants.textEmbeddingRateLimit(userId);
        tryAcquireOrThrow(key, TEXT_SEARCH_COOLDOWN);
    }

    /**
     * 检查图片搜索限流。未超限时通过 SETNX 原子设置冷却标记，超限时抛出 RateLimitException。
     */
    public void checkImageSearchRateLimit(long userId) {
        String key = RedisKeyConstants.imageEmbeddingRateLimit(userId);
        tryAcquireOrThrow(key, IMAGE_SEARCH_COOLDOWN);
    }

    /**
     * 检查物品发布限流。未超限时通过 SETNX 原子设置冷却标记，超限时抛出 RateLimitException。
     */
    public void checkItemCreateRateLimit(long userId) {
        String key = RedisKeyConstants.itemCreateRateLimit(userId);
        tryAcquireOrThrow(key, ITEM_CREATE_COOLDOWN);
    }

    /**
     * 检查聊天消息发送限流。未超限时通过 SETNX 原子设置冷却标记，超限时抛出 RateLimitException。
     */
    public void checkChatSendRateLimit(long userId) {
        String key = RedisKeyConstants.chatSendRateLimit(userId);
        tryAcquireOrThrow(key, CHAT_SEND_COOLDOWN);
    }

    /**
     * 检查用户注册限流（按 IP）。未超限时通过 SETNX 原子设置冷却标记，超限时抛出 RateLimitException。
     */
    public void checkUserRegisterRateLimit(String clientIp) {
        String key = RedisKeyConstants.userRegisterRateLimit(clientIp);
        tryAcquireOrThrow(key, USER_REGISTER_COOLDOWN);
    }

    /**
     * 检查认领提交限流。未超限时通过 SETNX 原子设置冷却标记，超限时抛出 RateLimitException。
     */
    public void checkClaimCreateRateLimit(long userId) {
        String key = RedisKeyConstants.claimCreateRateLimit(userId);
        tryAcquireOrThrow(key, CLAIM_CREATE_COOLDOWN);
    }

    /**
     * 核心原子操作：SET key value NX EX ttl
     * <ul>
     *   <li>返回 true → SETNX 成功，key 不存在，放行</li>
     *   <li>返回 false → SETNX 失败，key 已存在（冷却期内），获取剩余 TTL 并抛出异常</li>
     * </ul>
     */
    private void tryAcquireOrThrow(String key, Duration cooldown) {
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(System.currentTimeMillis()), cooldown);

        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Rate limit acquired, key={}, ttl={}", key, cooldown);
            return;
        }

        // 冷却期内 → 获取剩余 TTL 告知用户精确等待秒数
        Long remainingSeconds = stringRedisTemplate.getExpire(key);
        long waitSeconds = (remainingSeconds != null && remainingSeconds > 0)
                ? remainingSeconds
                : cooldown.getSeconds();

        log.warn("Rate limit exceeded, key={}, waitSeconds={}", key, waitSeconds);
        throw new RateLimitException(
                "请求过于频繁，请" + waitSeconds + "秒后重试"
        );
    }
}
