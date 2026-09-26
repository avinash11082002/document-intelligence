package com.docint.api.security;

import java.util.UUID;

/**
 * Immutable principal stored in the SecurityContext.
 * Provides strongly-typed access to the authenticated user's identity.
 */
public record AuthenticatedUser(UUID userId, String username, String rawToken) {}
