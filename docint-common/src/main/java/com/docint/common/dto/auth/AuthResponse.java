package com.docint.common.dto.auth;

import com.docint.common.enums.UserRole;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String username,
        String email,
        UserRole role
) {}
