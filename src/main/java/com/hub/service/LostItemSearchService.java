package com.hub.service;

import com.hub.common.PageResult;
import com.hub.domain.dto.request.ItemSearchRequest;
import com.hub.domain.vo.ItemSearchVo;

public interface LostItemSearchService {

    PageResult<ItemSearchVo> search(ItemSearchRequest request);
}
