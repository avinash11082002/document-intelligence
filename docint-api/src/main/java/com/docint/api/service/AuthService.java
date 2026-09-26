package com.docint.api.service;

import com.docint.common.dto.auth.AuthResponse;
import com.docint.common.dto.auth.LoginRequest;
import com.docint.common.dto.auth.RegisterRequest;
import com.docint.common.dto.auth.UserDto;

import java.util.UUID;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    /** Rotate refresh token: validate old token, issue new access + refresh pair. */
    AuthResponse refresh(String refreshToken);

    /** Revoke all refresh tokens for the given user (logout). */
    void logout(UUID userId);

    UserDto getProfile(UUID userId);
}
