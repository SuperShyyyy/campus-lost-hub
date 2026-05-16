package com.hub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.hub.cache.JsonRedisCacheService;
import com.hub.common.constant.CacheTtlConstants;
import com.hub.common.constant.RedisKeyConstants;
import com.hub.common.constant.UserStatusConstants;
import com.hub.domain.dto.request.UserLoginRequest;
import com.hub.domain.dto.request.UserRegisterRequest;
import com.hub.domain.dto.request.UserUpdateRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JsonRedisCacheService cache;

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

    @Override
    public TokenResponse login(UserLoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null) {
            throw new BizException(400, "用户名或密码错误");
        }
        if (UserStatusConstants.BANNED == user.getStatus()) {
            throw new ForbiddenException("账号已被封禁");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(400, "用户名或密码错误");
        }
        return new TokenResponse(jwtTokenProvider.createUserToken(user.getId()));
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
    public void update(long userId, UserUpdateRequest req) {
        boolean hasUsername = req.getUsername() != null && !req.getUsername().isBlank();
        boolean hasAvatar = req.getAvatar() != null;
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
        if (hasUsername) {
            String newName = req.getUsername().trim();
            if (!newName.equals(user.getUsername())) {
                Long dup = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, newName));
                if (dup != null && dup > 0) {
                    throw new BizException(400, "用户名已存在");
                }
                user.setUsername(newName);
            }
        }
        if (hasAvatar) {
            user.setAvatar(req.getAvatar());
        }
        userMapper.updateById(user);
        cache.delete(RedisKeyConstants.userMe(userId));
    }

    private UserMeVo toMeVo(User user) {
        return new UserMeVo(
                user.getId(),
                user.getUsername(),
                user.getAvatar(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    private void putCache(String key, Object value, java.time.Duration ttl) {
        try {
            cache.put(key, value, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis 序列化失败", e);
        }
    }
}
