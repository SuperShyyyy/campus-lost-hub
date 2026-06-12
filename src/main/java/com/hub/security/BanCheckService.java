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
 *   <li>每次请求时查 Redis ban:userId 标记，命中则直接拒绝（实时加速层）</li>
 *   <li>Redis miss 时回源 DB 校验账户状态，确认已封禁则补写 Redis 标记并拒绝</li>
 *   <li>Redis 作为实时封禁加速层，DB 是最终权威来源（防止 Redis 故障绕过封禁）</li>
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
     * 登录阶段不调用此方法（登录时 DB 已校验），仅供 Filter 层每次请求使用。
     */
    public void checkNotBanned(long userId) {
        String banKey = RedisKeyConstants.banUser(userId);

        // 1. Redis 快速判定
        String banned = stringRedisTemplate.opsForValue().get(banKey);
        if (banned != null) {
            log.debug("Redis ban hit, userId={}", userId);
            throw new ForbiddenException("账号已被封禁");
        }

        // 2. Redis miss → 回源 DB 做兜底校验（防止 Redis 故障/数据丢失导致绕过）
        User user = userMapper.selectById(userId);
        if (user != null && UserStatusConstants.BANNED == user.getStatus()) {
            // DB 确认已封禁但 Redis 无标记 → 补写 Redis（容错恢复）
            Duration ttl = jwtTokenProvider.getTokenExpireDuration();
            stringRedisTemplate.opsForValue().set(banKey, "1", ttl);
            log.warn("Redis ban miss but DB banned, patched redis, userId={}", userId);
            throw new ForbiddenException("账号已被封禁");
        }
    }

    /**
     * 封禁用户时写入 Redis 标记，TTL 与 JWT 过期时间一致。
     * 由 AdminServiceImpl.banUser() 调用。
     */
    public void markBannedInRedis(long userId) {
        String banKey = RedisKeyConstants.banUser(userId);
        Duration ttl = jwtTokenProvider.getTokenExpireDuration();
        stringRedisTemplate.opsForValue().set(banKey, "1", ttl);
        log.info("Redis ban marker set, userId={}, ttl={}", userId, ttl);
    }
}
