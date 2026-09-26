package com.docint.common.security;

import com.docint.common.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Shared JWT provider used by docint-api (issuance) and docint-query (validation).
 * Both services read the same secret from their environment.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpirationHours;

    public JwtTokenProvider(
            @Value("${docint.security.jwt.secret:docint-super-secret-key-that-must-be-at-least-256-bits-long-for-hmac-sha-256}") String secret,
            @Value("${docint.security.jwt.expiration-hours:1}") long accessTokenExpirationHours) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationHours = accessTokenExpirationHours;
    }

    /** Generate a signed access JWT containing userId, username, email, and role. */
    public String generateAccessToken(UUID userId, String username, String email, UserRole role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenExpirationHours, ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(getClaims(token).get("userId", String.class));
    }

    public UserRole getRoleFromToken(String token) {
        String role = getClaims(token).get("role", String.class);
        return role != null ? UserRole.valueOf(role) : UserRole.USER;
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).get("email", String.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
