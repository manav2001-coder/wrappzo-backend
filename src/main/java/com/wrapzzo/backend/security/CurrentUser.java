package com.wrapzzo.backend.security;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/** JwtAuthenticationFilter sets the authenticated user's id as the Authentication principal. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
