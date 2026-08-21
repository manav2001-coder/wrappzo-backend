package com.wrapzzo.backend.security;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/** JwtAuthenticationFilter sets the authenticated vendor's id as the Authentication principal for vendor tokens. */
public final class CurrentVendor {

    private CurrentVendor() {
    }

    public static UUID id() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
