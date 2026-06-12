package com.hub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.hub.cache.JsonRedisCacheService;
import com.hub.common.PageResult;
import com.hub.common.constant.CacheTtlConstants;
import com.hub.common.constant.ClaimStatusConstants;
import com.hub.common.constant.ItemStatusConstants;
import com.hub.common.constant.RedisKeyConstants;
import com.hub.common.constant.UserStatusConstants;
import com.hub.domain.dto.request.AdminLoginRequest;
import com.hub.domain.dto.request.ClaimAuditRequest;
import com.hub.domain.dto.response.TokenResponse;
import com.hub.domain.po.Admin;
import com.hub.domain.po.ClaimRecord;
import com.hub.domain.po.Item;
import com.hub.domain.po.User;
import com.hub.domain.vo.ClaimVo;
import com.hub.exception.BizException;
import com.hub.exception.NotFoundException;
import com.hub.mapper.AdminMapper;
import com.hub.mapper.ClaimRecordMapper;
import com.hub.mapper.ItemMapper;
import com.hub.mapper.UserMapper;
import com.hub.security.BanCheckService;
import com.hub.security.JwtTokenProvider;
import com.hub.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final UserMapper userMapper;
    private final ClaimRecordMapper claimRecordMapper;
    private final ItemMapper itemMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JsonRedisCacheService cache;
    private final BanCheckService banCheckService;

    @Override
    public TokenResponse login(AdminLoginRequest req) {
        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, req.getUsername()));
        if (admin == null) {
            throw new BizException(400, "管理员用户名或密码错误");
        }
        if (!passwordEncoder.matches(req.getPassword(), admin.getPassword())) {
            throw new BizException(400, "管理员用户名或密码错误");
        }
        return new TokenResponse(jwtTokenProvider.createAdminToken(admin.getId()));
    }

    @Override
    public PageResult<ClaimVo> claimsPage(int page, int size) {
        String key = RedisKeyConstants.adminClaims(page, size);
        var hit = cache.get(key, new TypeReference<PageResult<ClaimVo>>() {
        });
        if (!hit.isMiss() && !hit.isNullHit()) {
            return hit.value();
        }
        Page<ClaimRecord> p = new Page<>(page, size);
        claimRecordMapper.selectPage(
                p,
                new LambdaQueryWrapper<ClaimRecord>().orderByDesc(ClaimRecord::getCreatedAt)
        );
        PageResult<ClaimVo> result = new PageResult<>(
                p.getRecords().stream().map(this::toVo).toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize()
        );
        putCache(key, result, CacheTtlConstants.LIST_POSITIVE);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditClaim(long claimId, ClaimAuditRequest req) {
        ClaimRecord claim = claimRecordMapper.selectById(claimId);
        if (claim == null) {
            throw new NotFoundException("认领记录不存在");
        }
        if (claim.getStatus() == null || claim.getStatus() != ClaimStatusConstants.PENDING) {
            throw new BizException(400, "该认领已处理");
        }
        int newStatus = req.getStatus();
        if (newStatus == ClaimStatusConstants.APPROVED) {
            Item item = itemMapper.selectById(claim.getItemId());
            if (item == null) {
                throw new NotFoundException("物品不存在");
            }
            if (item.getStatus() == null || item.getStatus() != ItemStatusConstants.UNMATCHED) {
                throw new BizException(400, "该物品当前状态不可审核通过");
            }
            approveClaim(claim);
        } else {
            claim.setStatus(ClaimStatusConstants.REJECTED);
            claimRecordMapper.updateById(claim);
        }
        evictClaimCaches(claim);
        cache.delete(RedisKeyConstants.claimDetail(claimId));
        cache.deleteByPattern(RedisKeyConstants.adminClaimsPattern());
        cache.delete(RedisKeyConstants.itemDetail(claim.getItemId()));
        cache.deleteByPattern(RedisKeyConstants.itemListPattern());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void banUser(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        user.setStatus(UserStatusConstants.BANNED);
        userMapper.updateById(user);
        // 同步写 Redis ban 标记，使已签发的 token 立即失效
        banCheckService.markBannedInRedis(userId);
        cache.delete(RedisKeyConstants.userMe(userId));
    }

    private void approveClaim(ClaimRecord claim) {
        List<ClaimRecord> others = claimRecordMapper.selectList(
                new LambdaQueryWrapper<ClaimRecord>()
                        .eq(ClaimRecord::getItemId, claim.getItemId())
                        .eq(ClaimRecord::getStatus, ClaimStatusConstants.PENDING)
                        .ne(ClaimRecord::getId, claim.getId())
        );
        claim.setStatus(ClaimStatusConstants.APPROVED);
        claimRecordMapper.updateById(claim);
        for (ClaimRecord o : others) {
            o.setStatus(ClaimStatusConstants.REJECTED);
            claimRecordMapper.updateById(o);
            cache.delete(RedisKeyConstants.claimDetail(o.getId()));
            cache.delete(RedisKeyConstants.claimMy(o.getUserId()));
        }
        Item item = itemMapper.selectById(claim.getItemId());
        if (item != null) {
            item.setStatus(ItemStatusConstants.MATCHED);
            itemMapper.updateById(item);
        }
    }

    private ClaimVo toVo(ClaimRecord c) {
        ClaimVo v = new ClaimVo();
        v.setId(c.getId());
        v.setItemId(c.getItemId());
        v.setUserId(c.getUserId());
        v.setMessage(c.getMessage());
        v.setStatus(c.getStatus());
        v.setCreatedAt(c.getCreatedAt());
        return v;
    }

    private void evictClaimCaches(ClaimRecord claim) {
        cache.delete(RedisKeyConstants.claimMy(claim.getUserId()));
    }

    private void putCache(String key, Object value, Duration ttl) {
        try {
            cache.put(key, value, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis 序列化失败", e);
        }
    }
}
