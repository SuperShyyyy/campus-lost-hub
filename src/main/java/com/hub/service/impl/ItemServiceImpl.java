package com.hub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.hub.cache.JsonRedisCacheService;
import com.hub.common.PageResult;
import com.hub.common.constant.CacheTtlConstants;
import com.hub.common.constant.ItemStatusConstants;
import com.hub.common.constant.RedisKeyConstants;
import com.hub.config.AliyunConfig;
import com.hub.domain.dto.request.ItemCreateRequest;
import com.hub.domain.po.Item;
import com.hub.domain.repository.ItemVectorRepository;
import com.hub.domain.vo.ItemVo;
import com.hub.exception.BizException;
import com.hub.exception.ForbiddenException;
import com.hub.exception.NotFoundException;
import com.hub.mapper.ItemMapper;
import com.hub.service.EmbeddingService;
import com.hub.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;
    private final JsonRedisCacheService cache;
    private final EmbeddingService embeddingService;
    private final ItemVectorRepository itemVectorRepository;
    private final AliyunConfig aliyunConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(long userId, ItemCreateRequest req) {
        String title = req.getTitle().trim();
        String description = req.getDescription().trim();
        String location = req.getLocation() == null ? null : req.getLocation().trim();
        float[] embedding = embeddingService.embedItem(req.getType(), title, description, location);

        Item item = new Item();
        item.setUserId(userId);
        item.setType(req.getType());
        item.setTitle(title);
        item.setDescription(description);
        item.setLocation(location);
        item.setStatus(ItemStatusConstants.UNMATCHED);
        item.setEmbedding(null);
        itemMapper.insert(item);
        itemVectorRepository.updateItemEmbedding(item.getId(), embedding);
        cache.deleteByPattern(RedisKeyConstants.itemListPattern());
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(long userId, long itemId, ItemCreateRequest req) {
        Item existing = itemMapper.selectById(itemId);
        if (existing == null) {
            throw new NotFoundException("物品不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new ForbiddenException("只能更新自己发布的物品");
        }
        String title = req.getTitle().trim();
        String description = req.getDescription().trim();
        String location = req.getLocation() == null ? null : req.getLocation().trim();
        float[] embedding = embeddingService.embedItem(req.getType(), title, description, location);

        Item update = new Item();
        update.setId(itemId);
        update.setType(req.getType());
        update.setTitle(title);
        update.setDescription(description);
        update.setLocation(location);
        itemMapper.updateById(update);
        itemVectorRepository.updateItemEmbedding(itemId, embedding);
        cache.delete(RedisKeyConstants.itemDetail(itemId));
        cache.deleteByPattern(RedisKeyConstants.itemListPattern());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadImage(long userId, long itemId, MultipartFile file) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new NotFoundException("物品不存在");
        }
        if (!item.getUserId().equals(userId)) {
            throw new ForbiddenException("只能上传自己发布物品的图片");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("仅支持上传图片文件");
        }

        String imageUrl;
        try {
            imageUrl = aliyunConfig.uploadItemImage(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalStateException("读取上传图片失败", e);
        }

        Item update = new Item();
        update.setId(itemId);
        update.setImageUrl(imageUrl);
        itemMapper.updateById(update);
        cache.delete(RedisKeyConstants.itemDetail(itemId));
        cache.deleteByPattern(RedisKeyConstants.itemListPattern());
        return imageUrl;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(long userId, long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new NotFoundException("物品不存在");
        }
        if (!item.getUserId().equals(userId)) {
            throw new ForbiddenException("只能完结自己发布的物品");
        }
        if (item.getStatus() == null || item.getStatus() != ItemStatusConstants.MATCHED) {
            throw new BizException(400, "仅已匹配物品可完结");
        }
        Item update = new Item();
        update.setId(itemId);
        update.setStatus(ItemStatusConstants.COMPLETED);
        itemMapper.updateById(update);
        cache.delete(RedisKeyConstants.itemDetail(itemId));
        cache.deleteByPattern(RedisKeyConstants.itemListPattern());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long userId, long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new NotFoundException("物品不存在");
        }
        if (!item.getUserId().equals(userId)) {
            throw new ForbiddenException("只能删除自己发布的物品");
        }
        itemMapper.deleteById(itemId);
        cache.delete(RedisKeyConstants.itemDetail(itemId));
        cache.deleteByPattern(RedisKeyConstants.itemListPattern());
    }

    @Override
    public PageResult<ItemVo> list(Integer type, Integer status, int page, int size) {
        String key = RedisKeyConstants.itemList(type, status, page, size);
        var hit = cache.get(key, new TypeReference<PageResult<ItemVo>>() {
        });
        if (!hit.isMiss() && !hit.isNullHit()) {
            return hit.value();
        }
        LambdaQueryWrapper<Item> w = new LambdaQueryWrapper<Item>().orderByDesc(Item::getCreatedAt);
        if (type != null) {
            w.eq(Item::getType, type);
        }
        if (status != null) {
            w.eq(Item::getStatus, status);
        }
        Page<Item> p = new Page<>(page, size);
        itemMapper.selectPage(p, w);
        PageResult<ItemVo> result = new PageResult<>(
                p.getRecords().stream().map(this::toVo).toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize()
        );
        putCache(key, result, CacheTtlConstants.LIST_POSITIVE);
        return result;
    }

    @Override
    public PageResult<ItemVo> myItems(long userId, Integer type, Integer status, int page, int size) {
        LambdaQueryWrapper<Item> w = new LambdaQueryWrapper<Item>()
                .eq(Item::getUserId, userId)
                .orderByDesc(Item::getCreatedAt);
        if (type != null) {
            w.eq(Item::getType, type);
        }
        if (status != null) {
            w.eq(Item::getStatus, status);
        }
        Page<Item> p = new Page<>(page, size);
        itemMapper.selectPage(p, w);
        return new PageResult<>(
                p.getRecords().stream().map(this::toVo).toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize()
        );
    }

    @Override
    public ItemVo getById(long id) {
        String key = RedisKeyConstants.itemDetail(id);
        var hit = cache.get(key, ItemVo.class);
        if (!hit.isMiss() && !hit.isNullHit()) {
            return hit.value();
        }
        if (hit.isNullHit()) {
            throw new NotFoundException("物品不存在");
        }
        Item item = itemMapper.selectById(id);
        if (item == null) {
            cache.putNull(key, CacheTtlConstants.NULL_ENTRY);
            throw new NotFoundException("物品不存在");
        }
        ItemVo vo = toVo(item);
        putCache(key, vo, CacheTtlConstants.DEFAULT_POSITIVE);
        return vo;
    }

    private ItemVo toVo(Item item) {
        ItemVo vo = new ItemVo();
        vo.setId(item.getId());
        vo.setUserId(item.getUserId());
        vo.setType(item.getType());
        vo.setTitle(item.getTitle());
        vo.setDescription(item.getDescription());
        vo.setLocation(item.getLocation());
        vo.setStatus(item.getStatus());
        vo.setImageUrl(item.getImageUrl());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private void putCache(String key, Object value, java.time.Duration ttl) {
        try {
            cache.put(key, value, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis 序列化失败", e);
        }
    }
}
