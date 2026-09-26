package com.docint.api.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Utility for extracting the current user from the SecurityContext.
 * Never accept userId from request parameters or headers.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /** Returns the authenticated user's UUID, or throws 401 if not authenticated. */
    public static UUID currentUserId() {
        return currentUser().userId();
    }

    /** Returns the authenticated user principal. */
    public static AuthenticatedUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return user;
    }
}
