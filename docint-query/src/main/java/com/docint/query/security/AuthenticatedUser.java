package com.docint.query.security;

import java.util.UUID;

/** Immutable principal stored in the SecurityContext for query service requests. */
public record AuthenticatedUser(UUID userId, String username, String rawToken) {}
