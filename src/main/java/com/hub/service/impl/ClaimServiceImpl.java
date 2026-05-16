package com.hub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.hub.cache.JsonRedisCacheService;
import com.hub.common.PageResult;
import com.hub.common.constant.CacheTtlConstants;
import com.hub.common.constant.ClaimStatusConstants;
import com.hub.common.constant.ItemStatusConstants;
import com.hub.common.constant.RedisKeyConstants;
import com.hub.domain.dto.request.ClaimCreateRequest;
import com.hub.domain.po.ClaimRecord;
import com.hub.domain.po.Item;
import com.hub.domain.vo.ClaimVo;
import com.hub.exception.BizException;
import com.hub.exception.ForbiddenException;
import com.hub.exception.NotFoundException;
import com.hub.mapper.ClaimRecordMapper;
import com.hub.mapper.ItemMapper;
import com.hub.service.ClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClaimServiceImpl implements ClaimService {

    private final ClaimRecordMapper claimRecordMapper;
    private final ItemMapper itemMapper;
    private final JsonRedisCacheService cache;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(long userId, ClaimCreateRequest req) {
        Item item = itemMapper.selectById(req.getItemId());
        if (item == null) {
            throw new NotFoundException("物品不存在");
        }
        if (item.getUserId().equals(userId)) {
            throw new ForbiddenException("不能认领自己发布的物品");
        }
        if (item.getStatus() == null || item.getStatus() != ItemStatusConstants.UNMATCHED) {
            throw new BizException(400, "该物品已匹配或已完结，暂不可认领");
        }
        Long pending = claimRecordMapper.selectCount(
                new LambdaQueryWrapper<ClaimRecord>()
                        .eq(ClaimRecord::getItemId, req.getItemId())
                        .eq(ClaimRecord::getUserId, userId)
                        .eq(ClaimRecord::getStatus, ClaimStatusConstants.PENDING)
        );
        if (pending != null && pending > 0) {
            throw new BizException(400, "已存在待审核的认领申请");
        }
        ClaimRecord record = new ClaimRecord();
        record.setItemId(req.getItemId());
        record.setUserId(userId);
        record.setMessage(req.getMessage().trim());
        record.setStatus(ClaimStatusConstants.PENDING);
        claimRecordMapper.insert(record);
        cache.delete(RedisKeyConstants.claimMy(userId));
        cache.deleteByPattern(RedisKeyConstants.adminClaimsPattern());
        return record.getId();
    }

    @Override
    public PageResult<ClaimVo> myClaims(long userId, int page, int size) {
        Page<ClaimRecord> p = new Page<>(page, size);
        claimRecordMapper.selectPage(
                p,
                new LambdaQueryWrapper<ClaimRecord>()
                        .eq(ClaimRecord::getUserId, userId)
                        .orderByDesc(ClaimRecord::getCreatedAt)
        );
        return new PageResult<>(
                p.getRecords().stream().map(this::toVo).toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize()
        );
    }

    @Override
    public ClaimVo getById(long userId, long claimId) {
        String key = RedisKeyConstants.claimDetail(claimId);
        var hit = cache.get(key, ClaimVo.class);
        if (!hit.isMiss() && !hit.isNullHit()) {
            ClaimVo vo = hit.value();
            assertOwner(userId, vo);
            return vo;
        }
        if (hit.isNullHit()) {
            throw new NotFoundException("认领记录不存在");
        }
        ClaimRecord record = claimRecordMapper.selectById(claimId);
        if (record == null) {
            cache.putNull(key, CacheTtlConstants.NULL_ENTRY);
            throw new NotFoundException("认领记录不存在");
        }
        ClaimVo vo = toVo(record);
        putCache(key, vo, CacheTtlConstants.DEFAULT_POSITIVE);
        assertOwner(userId, vo);
        return vo;
    }

    private void assertOwner(long userId, ClaimVo vo) {
        if (vo.getUserId() == null || !vo.getUserId().equals(userId)) {
            throw new ForbiddenException("无权查看该认领记录");
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

    private void putCache(String key, Object value, java.time.Duration ttl) {
        try {
            cache.put(key, value, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis 序列化失败", e);
        }
    }
}
