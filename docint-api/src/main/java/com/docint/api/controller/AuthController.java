package com.docint.api.controller;

import com.docint.api.security.SecurityUtils;
import com.docint.api.service.AuthService;
import com.docint.common.dto.auth.AuthResponse;
import com.docint.common.dto.auth.LoginRequest;
import com.docint.common.dto.auth.RegisterRequest;
import com.docint.common.dto.auth.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, token refresh and logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account",
            description = "Creates a new user account and returns an access + refresh token pair")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login",
            description = "Authenticates with username or email + password. Returns access + refresh tokens.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token",
            description = "Exchange a valid refresh token for a new access + refresh token pair (token rotation).")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AuthResponse response = authService.refresh(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revokes all refresh tokens for the current user.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Void> logout() {
        authService.logout(SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile",
            description = "Returns profile information for the authenticated user. Identity is from JWT — never from request params.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<UserDto> getProfile() {
        UserDto profile = authService.getProfile(SecurityUtils.currentUserId());
        return ResponseEntity.ok(profile);
    }
}
