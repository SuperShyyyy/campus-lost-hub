package com.hub.service;

import com.hub.common.PageResult;
import com.hub.domain.dto.request.ClaimCreateRequest;
import com.hub.domain.vo.ClaimVo;

public interface ClaimService {

    Long create(long userId, ClaimCreateRequest req);

    PageResult<ClaimVo> myClaims(long userId, int page, int size);

    ClaimVo getById(long userId, long claimId);
}
