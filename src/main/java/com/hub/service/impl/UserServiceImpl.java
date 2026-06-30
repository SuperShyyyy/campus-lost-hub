package com.hub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.hub.cache.JsonRedisCacheService;
import com.hub.common.constant.CacheTtlConstants;
import com.hub.common.constant.RedisKeyConstants;
import com.hub.common.constant.UserStatusConstants;
import com.hub.config.AliyunConfig;
import com.hub.domain.dto.request.ChangePasswordRequest;
import com.hub.domain.dto.request.UserLoginRequest;
import com.hub.domain.dto.request.UserRegisterRequest;
import com.hub.domain.dto.response.TokenResponse;
import com.hub.domain.po.User;
import com.hub.domain.vo.UserMeVo;
import com.hub.exception.BizException;
import com.hub.exception.ForbiddenException;
import com.hub.exception.NotFoundException;
import com.hub.mapper.UserMapper;
import com.hub.security.JwtTokenProvider;
import com.hub.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JsonRedisCacheService cache;
    private final StringRedisTemplate stringRedisTemplate;
    private final AliyunConfig aliyunConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterRequest req) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (count != null && count > 0) {
            throw new BizException(400, "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername().trim());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setStatus(UserStatusConstants.NORMAL);
        userMapper.insert(user);
    }

    /**
     * 用户登录。
     * <ol>
     *   <li>校验用户名密码 & 封禁状态（DB 直查）</li>
     *   <li>读取或初始化 tokenVersion（Redis GET → null 则为 0）</li>
     *   <li>生成新的 sessionId（UUID）</li>
     *   <li>写入 Redis：user:session = sessionId（TLL=JWT过期时间）</li>
     *   <li>签发 JWT（含 userId + sessionId + tokenVersion）</li>
     * </ol>
     * <p>
     * 单设备登录互踢：新登录的 sessionId 覆盖旧值，旧设备的 sessionId 不匹配 → 被踢。
     */
    @Override
    public TokenResponse login(UserLoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null) {
            throw new BizException(400, "用户名或密码错误");
        }
        if (UserStatusConstants.BANNED == user.getStatus()) {
            markBannedIfAbsent(user.getId());
            throw new ForbiddenException("账号已被封禁");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(400, "用户名或密码错误");
        }

        long userId = user.getId();
        Duration ttl = jwtTokenProvider.getTokenExpireDuration();

        // 1. 读取当前 tokenVersion（首次登录为 0）
        long tokenVersion = getTokenVersion(userId);

        // 2. 生成新 sessionId，写入 Redis（覆盖旧值 → 旧设备被踢）
        String sessionId = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(RedisKeyConstants.userSession(userId), sessionId, ttl);

        // 3. 签发 JWT
        String jwt = jwtTokenProvider.createUserToken(userId, sessionId, tokenVersion);
        return new TokenResponse(jwt);
    }

    /**
     * 读取 Redis 中的 tokenVersion，不存在则初始化并返回 0。
     * 使用 SETNX 原子初始化，避免覆盖并发的 INCR 操作。
     */
    private long getTokenVersion(long userId) {
        String key = RedisKeyConstants.userTokenVersion(userId);
        String val = stringRedisTemplate.opsForValue().get(key);
        if (val != null) {
            return Long.parseLong(val);
        }
        // 首次登录：SETNX 原子初始化，失败说明并发操作已修改 key，重新读取
        Boolean set = stringRedisTemplate.opsForValue().setIfAbsent(key, "0");
        if (Boolean.TRUE.equals(set)) {
            return 0L;
        }
        val = stringRedisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : 0L;
    }

    private void markBannedIfAbsent(long userId) {
        String banKey = RedisKeyConstants.userBan(userId);
        Boolean exists = stringRedisTemplate.hasKey(banKey);
        if (exists == null || !exists) {
            Duration ttl = jwtTokenProvider.getTokenExpireDuration();
            stringRedisTemplate.opsForValue().set(banKey, "1", ttl);
        }
    }

    @Override
    public UserMeVo getMe(long userId) {
        String key = RedisKeyConstants.userMe(userId);
        var hit = cache.get(key, UserMeVo.class);
        if (!hit.isMiss() && !hit.isNullHit()) {
            return hit.value();
        }
        if (hit.isNullHit()) {
            throw new NotFoundException("用户不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            cache.putNull(key, CacheTtlConstants.NULL_ENTRY);
            throw new NotFoundException("用户不存在");
        }
        UserMeVo vo = toMeVo(user);
        putCache(key, vo, CacheTtlConstants.DEFAULT_POSITIVE);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(long userId, MultipartFile avatar, String username) {
        boolean hasUsername = username != null && !username.isBlank();
        boolean hasAvatar = avatar != null && !avatar.isEmpty();
        if (!hasUsername && !hasAvatar) {
            throw new BizException(400, "请至少填写要更新的字段");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        if (UserStatusConstants.BANNED == user.getStatus()) {
            throw new ForbiddenException("账号已被封禁");
        }

        String avatarUrl = null;
        if (hasAvatar) {
            try {
                avatarUrl = aliyunConfig.uploadUserAvatar(avatar.getBytes(), avatar.getOriginalFilename());
            } catch (IOException e) {
                throw new BizException(400, "头像文件读取失败");
            }
        }

        try {
            if (hasUsername) {
                String newName = username.trim();
                if (!newName.equals(user.getUsername())) {
                    Long dup = userMapper.selectCount(
                            new LambdaQueryWrapper<User>().eq(User::getUsername, newName));
                    if (dup != null && dup > 0) {
                        throw new BizException(400, "用户名已存在");
                    }
                    user.setUsername(newName);
                }
            }
            if (avatarUrl != null) {
                user.setAvatar(avatarUrl);
            }
            userMapper.updateById(user);
            cache.delete(RedisKeyConstants.userMe(userId));
        } catch (Exception e) {
            if (avatarUrl != null) {
                aliyunConfig.deleteByFileUrl(avatarUrl);
            }
            throw e;
        }
    }

    /**
     * 登出。
     * <ul>
     *   <li>INCR tokenVersion → 所有旧 Token 立即失效</li>
     *   <li>删除 session → 单设备会话清除</li>
     *   <li>清除用户缓存</li>
     * </ul>
     */
    @Override
    public void logout(long userId) {
        stringRedisTemplate.opsForValue().increment(RedisKeyConstants.userTokenVersion(userId));
        stringRedisTemplate.delete(RedisKeyConstants.userSession(userId));
        cache.delete(RedisKeyConstants.userMe(userId));
    }

    /**
     * 修改密码。
     * 密码写入 DB 成功后：
     * <ul>
     *   <li>INCR tokenVersion → 所有设备 Token 失效，强制重新登录</li>
     *   <li>删除 session</li>
     *   <li>清除用户缓存</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(long userId, ChangePasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        if (UserStatusConstants.BANNED == user.getStatus()) {
            markBannedIfAbsent(userId);
            throw new ForbiddenException("账号已被封禁");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BizException(400, "原密码错误");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);

        // DB 写入成功后失效所有 Token
        stringRedisTemplate.opsForValue().increment(RedisKeyConstants.userTokenVersion(userId));
        stringRedisTemplate.delete(RedisKeyConstants.userSession(userId));
        cache.delete(RedisKeyConstants.userMe(userId));
    }

    // ========== 辅助方法 ==========

    private UserMeVo toMeVo(User user) {
        return new UserMeVo(
                user.getId(),
                user.getUsername(),
                user.getAvatar(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    private void putCache(String key, Object value, Duration ttl) {
        try {
            cache.put(key, value, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis 序列化失败", e);
        }
    }
}
