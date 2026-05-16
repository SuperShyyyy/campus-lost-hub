package com.hub.service;

import com.hub.domain.dto.request.UserLoginRequest;
import com.hub.domain.dto.request.UserRegisterRequest;
import com.hub.domain.dto.request.UserUpdateRequest;
import com.hub.domain.dto.response.TokenResponse;
import com.hub.domain.vo.UserMeVo;

public interface UserService {

    void register(UserRegisterRequest req);

    TokenResponse login(UserLoginRequest req);

    UserMeVo getMe(long userId);

    void update(long userId, UserUpdateRequest req);
}
