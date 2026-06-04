package com.hub.service;

import com.hub.common.PageResult;
import com.hub.domain.dto.request.ItemCreateRequest;
import com.hub.domain.vo.ItemVo;
import org.springframework.web.multipart.MultipartFile;

public interface ItemService {

    Long create(long userId, ItemCreateRequest req);

    /**
     * 更新当前用户已发布物品的标题、类型、描述与地点，并同步向量检索用 text_embedding。
     */
    void update(long userId, long itemId, ItemCreateRequest req);

    String uploadImage(long userId, long itemId, MultipartFile file);

    void complete(long userId, long itemId);

    void delete(long userId, long itemId);

    PageResult<ItemVo> list(Integer type, Integer status, int page, int size);

    PageResult<ItemVo> myItems(long userId, Integer type, Integer status, int page, int size);

    ItemVo getById(long id);
}
