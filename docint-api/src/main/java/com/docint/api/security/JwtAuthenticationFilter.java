package com.docint.api.security;

import com.docint.common.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extracts Bearer JWT from Authorization header, validates it, and populates
 * the SecurityContext with the authenticated user's identity and role.
 * This is the SINGLE point where user identity enters the system from HTTP.
 * All downstream services must read from SecurityContext — never from request params.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                var userId = jwtTokenProvider.getUserIdFromToken(token);
                var username = jwtTokenProvider.getUsernameFromToken(token);
                var role = jwtTokenProvider.getRoleFromToken(token);

                var authority = new SimpleGrantedAuthority("ROLE_" + role.name());
                var authentication = new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(userId, username, token),
                        null,
                        List.of(authority)
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated request for user={} role={}", username, role);

            } catch (Exception e) {
                log.warn("JWT claims extraction failed: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
