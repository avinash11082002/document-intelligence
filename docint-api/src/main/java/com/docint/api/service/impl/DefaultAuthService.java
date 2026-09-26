package com.docint.api.service.impl;

import com.docint.api.repository.RefreshTokenRepository;
import com.docint.api.repository.UserRepository;
import com.docint.api.service.AuthService;
import com.docint.common.dto.auth.AuthResponse;
import com.docint.common.dto.auth.LoginRequest;
import com.docint.common.dto.auth.RegisterRequest;
import com.docint.common.dto.auth.UserDto;
import com.docint.common.entity.RefreshToken;
import com.docint.common.entity.User;
import com.docint.common.enums.UserRole;
import com.docint.common.security.JwtTokenProvider;
import com.docint.common.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuthService implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${docint.security.refresh-token.expiration-days:30}")
    private int refreshTokenExpirationDays;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email '" + email + "' is already registered");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered new user: {} (id={})", saved.getUsername(), saved.getId());

        return buildAuthResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.usernameOrEmail().trim().toLowerCase();

        Optional<User> userOpt = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier));

        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Account '" + identifier + "' does not exist. Please check your username or create an account.");
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Incorrect password for '" + identifier + "'. Please check your password and try again.");
        }

        log.info("User authenticated: {} (id={})", user.getUsername(), user.getId());
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = HashUtil.sha256(rawRefreshToken.getBytes());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        // Rotate: revoke old token, issue new pair
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("All refresh tokens revoked for user id={}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }

    // ---- helpers ----

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getEmail(), user.getRole());

        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = HashUtil.sha256(rawRefreshToken.getBytes());

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(refreshTokenExpirationDays, ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken,
                user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
