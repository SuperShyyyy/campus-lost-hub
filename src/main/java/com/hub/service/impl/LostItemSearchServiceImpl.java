package com.hub.service.impl;

import com.hub.common.PageResult;
import com.hub.domain.dto.request.ItemSearchRequest;
import com.hub.domain.po.Item;
import com.hub.domain.repository.ItemSearchHit;
import com.hub.domain.repository.ItemVectorRepository;
import com.hub.domain.vo.ItemSearchVo;
import com.hub.service.EmbeddingService;
import com.hub.service.LostItemSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LostItemSearchServiceImpl implements LostItemSearchService {

    private static final int MAX_TOTAL = 20;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final double DEFAULT_MIN_SCORE = 0.6D;

    private final EmbeddingService embeddingService;
    private final ItemVectorRepository repository;

    @Override
    public PageResult<ItemSearchVo> search(ItemSearchRequest request) {
        int page = request.getPage() == null ? DEFAULT_PAGE : request.getPage();
        int size = request.getSize() == null ? DEFAULT_SIZE : request.getSize();
        double minScore = request.getMinScore() == null ? DEFAULT_MIN_SCORE : request.getMinScore();
        int offset = (page - 1) * size;
        if (offset >= MAX_TOTAL) {
            return new PageResult<>(List.of(), 0, page, size);
        }

        float[] vector = embeddingService.embedQuery(request.getQuery());
        long total = Math.min(repository.countByText(vector, minScore), MAX_TOTAL);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, page, size);
        }

        int limit = Math.min(size, MAX_TOTAL - offset);
        if (limit <= 0) {
            return new PageResult<>(List.of(), total, page, size);
        }

        List<ItemSearchVo> records = repository.searchByText(vector, minScore, limit, offset)
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
