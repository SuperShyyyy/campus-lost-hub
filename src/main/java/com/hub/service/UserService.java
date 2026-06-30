package com.hub.service;

import com.hub.domain.dto.request.ChangePasswordRequest;
import com.hub.domain.dto.request.UserLoginRequest;
import com.hub.domain.dto.request.UserRegisterRequest;
import com.hub.domain.dto.response.TokenResponse;
import com.hub.domain.vo.UserMeVo;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    void register(UserRegisterRequest req);

    TokenResponse login(UserLoginRequest req);

    UserMeVo getMe(long userId);

    void update(long userId, MultipartFile avatar, String username);

    void logout(long userId);

    void changePassword(long userId, ChangePasswordRequest req);
}
