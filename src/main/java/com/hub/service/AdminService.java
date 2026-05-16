package com.hub.service;

import com.hub.common.PageResult;
import com.hub.domain.dto.request.AdminLoginRequest;
import com.hub.domain.dto.request.ClaimAuditRequest;
import com.hub.domain.dto.response.TokenResponse;
import com.hub.domain.vo.ClaimVo;

public interface AdminService {

    TokenResponse login(AdminLoginRequest req);

    PageResult<ClaimVo> claimsPage(int page, int size);

    void auditClaim(long claimId, ClaimAuditRequest req);

    void banUser(long userId);
}
