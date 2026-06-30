package com.hub.security;

import com.hub.common.constant.RedisKeyConstants;
import com.hub.common.constant.UserStatusConstants;
import com.hub.domain.po.User;
import com.hub.exception.ForbiddenException;
import com.hub.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 封禁检查服务：
 * <ul>
 *   <li>每次请求时查 Redis user:ban:{userId} 标记，命中则直接拒绝（实时加速层）</li>
 *   <li>Redis 正常返回 null → 放行（不落 DB，减少数据库压力）</li>
 *   <li>Redis 故障（连接超时/宕机等）→ 回源 DB 校验作为兜底</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BanCheckService {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 检查指定用户是否已被封禁，封禁则抛出 ForbiddenException。
     * <ul>
     *   <li>Redis 命中封禁标记 → 直接拒绝</li>
     *   <li>Redis 正常返回 null → 放行（无封禁）</li>
     *   <li>Redis 故障（连接失败等）→ 回源 DB 兜底校验</li>
     * </ul>
     */
    public void checkNotBanned(long userId) {
        String banKey = RedisKeyConstants.userBan(userId);

        // 1. Redis 快速判定
        try {
            String banned = stringRedisTemplate.opsForValue().get(banKey);
            if (banned != null) {
                log.debug("Redis ban hit, userId={}", userId);
                throw new ForbiddenException("账号已被封禁");
            }
            // Redis 正常返回 null → 无封禁标记 → 放行
            return;
        } catch (ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to DB for ban check, userId={}", userId, e);
        }

        // 2. Redis 故障 → 回源 DB 兜底
        User user = userMapper.selectById(userId);
        if (user != null && UserStatusConstants.BANNED == user.getStatus()) {
            log.warn("DB confirms banned while Redis was down, userId={}", userId);
            throw new ForbiddenException("账号已被封禁");
        }
    }

    /**
     * 封禁用户时写入 Redis 标记，TTL 与 JWT 过期时间一致。
     * 由 AdminServiceImpl.banUser() 调用。
     */
    public void markBannedInRedis(long userId) {
        String banKey = RedisKeyConstants.userBan(userId);
        Duration ttl = jwtTokenProvider.getTokenExpireDuration();
        stringRedisTemplate.opsForValue().set(banKey, "1", ttl);
        log.info("Redis ban marker set, userId={}, ttl={}", userId, ttl);
    }
}
