package com.hub.service;

import com.hub.common.PageResult;
import com.hub.domain.vo.ItemSearchVo;
import org.springframework.web.multipart.MultipartFile;

public interface ImageSearchService {

    PageResult<ItemSearchVo> search(MultipartFile file, int page, int size, Double minScore);
}
