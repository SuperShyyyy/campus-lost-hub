package com.hub.security;

import com.hub.common.constant.RedisKeyConstants;
import com.hub.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 Redis ZSET + Lua 的滑动窗口请求限流。
 * <p>
 * 所有限流规则均为 60s 滑动窗口，按用户粒度（注册按 IP）：
 * 文本搜索 10 次、图片搜索 5 次、物品发布 5 次、
 * 聊天发送 20 次、用户注册 3 次、认领提交 5 次。
 * </p>
 */
@Slf4j
@Service
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<List> slidingWindowScript;

    // ========== 滑动窗口参数 ==========

    private static final Duration WINDOW_60S = Duration.ofSeconds(60);

    private static final int TEXT_SEARCH_MAX = 10;
    private static final int IMAGE_SEARCH_MAX = 5;
    private static final int ITEM_CREATE_MAX = 5;
    private static final int CHAT_SEND_MAX = 20;
    private static final int USER_REGISTER_MAX = 3;
    private static final int CLAIM_CREATE_MAX = 5;

    // ========== Lua 脚本 ==========

    /**
     * ZSET 滑动窗口 Lua 脚本（原子执行）。
     * <pre>
     * KEYS[1]: rate limit key
     * ARGV[1]: now (ms)
     * ARGV[2]: windowMs
     * ARGV[3]: maxRequests
     * ARGV[4]: unique member
     * 返回: [0, newCount, "0"] 放行  /  [1, currentCount, nextAvailableMs] 限流
     * </pre>
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local maxRequests = tonumber(ARGV[3])
            local member = ARGV[4]
            local windowStart = now - windowMs
            redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)
            local count = redis.call('ZCARD', key)
            if count >= maxRequests then
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                local nextAvailable = windowStart
                if oldest and #oldest >= 2 then
                    nextAvailable = tonumber(oldest[2]) + windowMs
                end
                return {1, count, tostring(nextAvailable)}
            end
            redis.call('ZADD', key, now, member)
            redis.call('EXPIRE', key, math.ceil(windowMs / 1000) + 1)
            return {0, count + 1, '0'}
            """;

    public RateLimitService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(SLIDING_WINDOW_SCRIPT);
        script.setResultType(List.class);
        this.slidingWindowScript = script;
    }

    // ========== 公开限流方法 ==========

    /** 文本搜索：60s 内最多 10 次 */
    public void checkTextSearchRateLimit(long userId) {
        check(RedisKeyConstants.textSearchRateLimit(userId), WINDOW_60S, TEXT_SEARCH_MAX, "文本搜索");
    }

    /** 图片搜索：60s 内最多 5 次 */
    public void checkImageSearchRateLimit(long userId) {
        check(RedisKeyConstants.imageSearchRateLimit(userId), WINDOW_60S, IMAGE_SEARCH_MAX, "图片搜索");
    }

    /** 物品发布：60s 内最多 5 次 */
    public void checkItemCreateRateLimit(long userId) {
        check(RedisKeyConstants.itemCreateRateLimit(userId), WINDOW_60S, ITEM_CREATE_MAX, "物品发布");
    }

    /** 聊天消息发送：60s 内最多 20 次 */
    public void checkChatSendRateLimit(long userId) {
        check(RedisKeyConstants.chatSendRateLimit(userId), WINDOW_60S, CHAT_SEND_MAX, "聊天");
    }

    /** 用户注册：60s 内最多 3 次（按 IP） */
    public void checkUserRegisterRateLimit(String clientIp) {
        check(RedisKeyConstants.userRegisterRateLimit(clientIp), WINDOW_60S, USER_REGISTER_MAX, "注册");
    }

    /** 认领提交：60s 内最多 5 次 */
    public void checkClaimCreateRateLimit(long userId) {
        check(RedisKeyConstants.claimCreateRateLimit(userId), WINDOW_60S, CLAIM_CREATE_MAX, "认领");
    }

    // ========== 滑动窗口核心逻辑 ==========

    private void check(String key, Duration window, int maxRequests, String label) {
        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        String member = now + ":" + ThreadLocalRandom.current().nextInt(1000);

        @SuppressWarnings("unchecked")
        List<Object> result = stringRedisTemplate.execute(
                slidingWindowScript,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(windowMs),
                String.valueOf(maxRequests),
                member
        );

        if (result == null || result.isEmpty()) {
            log.warn("Sliding window script returned empty, key={}", key);
            return; // 脚本异常时放行
        }

        long code = ((Number) result.get(0)).longValue();
        long count = result.size() > 1 ? ((Number) result.get(1)).longValue() : 0;

        if (code == 0) {
            log.debug("Sliding window passed, label={}, key={}, count={}/{}", label, key, count, maxRequests);
            return;
        }

        long nextAvailableMs = 0;
        if (result.size() > 2 && result.get(2) != null) {
            try {
                nextAvailableMs = Long.parseLong(result.get(2).toString());
            } catch (NumberFormatException ignored) {
            }
        }
        long waitSeconds = Math.max(1, (nextAvailableMs - now + 999) / 1000);

        log.warn("Sliding window exceeded, label={}, key={}, count={}/{}, waitSeconds={}",
                label, key, count, maxRequests, waitSeconds);
        throw new RateLimitException(label + "请求过于频繁，请" + waitSeconds + "秒后重试");
    }
}
