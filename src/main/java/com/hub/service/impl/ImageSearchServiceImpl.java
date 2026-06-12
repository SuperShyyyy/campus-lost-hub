package com.hub.service.impl;

import com.hub.common.PageResult;
import com.hub.config.ClipProperties;
import com.hub.domain.po.Item;
import com.hub.domain.repository.ItemSearchHit;
import com.hub.domain.repository.ItemVectorRepository;
import com.hub.domain.vo.ItemSearchVo;
import com.hub.exception.BizException;
import com.hub.service.ImageEmbeddingService;
import com.hub.service.ImageSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageSearchServiceImpl implements ImageSearchService {

    private static final int MAX_TOTAL = 20;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final double DEFAULT_MIN_SCORE = 0.5D;
    /** 图片搜索上传大小上限 10MB，超过此值提示用户压缩后重试 */
    private static final long MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final ClipProperties clipProperties;
    private final ImageEmbeddingService imageEmbeddingService;
    private final ItemVectorRepository repository;

    @Override
    public PageResult<ItemSearchVo> search(MultipartFile file, int page, int size, Double minScore) {
        if (!clipProperties.isEnabled()) {
            throw new BizException(501, "图搜功能未启用（lost-hub.clip.enabled=false）");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("仅支持上传图片文件");
        }
        long fileSize = file.getSize();
        if (fileSize > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("图片过大（最大10MB），请压缩后重试");
        }

        double threshold = minScore == null ? DEFAULT_MIN_SCORE : minScore;
        int offset = (page - 1) * size;
        if (offset >= MAX_TOTAL) {
            return new PageResult<>(List.of(), 0, page, size);
        }

        float[] vector;
        try {
            vector = imageEmbeddingService.embedImage(file.getBytes(), contentType);
        } catch (IOException e) {
            throw new IllegalStateException("读取上传图片失败", e);
        }

        long total = Math.min(repository.countByImage(vector, threshold), MAX_TOTAL);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, page, size);
        }

        int limit = Math.min(size, MAX_TOTAL - offset);
        if (limit <= 0) {
            return new PageResult<>(List.of(), total, page, size);
        }

        List<ItemSearchVo> records = repository.searchByImage(vector, threshold, limit, offset)
                .stream()
                .map(this::toSearchVo)
                .toList();
        return new PageResult<>(records, total, page, size);
    }

    private ItemSearchVo toSearchVo(ItemSearchHit hit) {
        Item item = hit.item();
        ItemSearchVo vo = new ItemSearchVo();
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
        vo.setScore(hit.score());
        vo.setDistance(hit.distance());
        return vo;
    }
}
