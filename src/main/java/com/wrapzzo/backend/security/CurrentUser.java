package com.wrapzzo.backend.security;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/** JwtAuthenticationFilter sets the authenticated user's id as the Authentication principal. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Whether the signed-in user holds this permission key (the authorities are the keys, resolved fresh on every request). */
    public static boolean can(String permissionKey) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a -> permissionKey.equals(a.getAuthority()));
    }

    public static UUID id() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
