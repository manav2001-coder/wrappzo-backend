package com.wrapzzo.backend.security;

import java.util.UUID;

public record UserPrincipalClaims(UUID userId, UUID roleId, UUID companyId) implements PrincipalClaims {
}
