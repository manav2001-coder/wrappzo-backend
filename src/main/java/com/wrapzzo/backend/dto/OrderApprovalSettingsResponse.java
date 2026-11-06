package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.ArtworkApprovalScope;
import java.util.List;
import java.util.UUID;

public record OrderApprovalSettingsResponse(boolean enabled, UUID roleId, String roleName, ArtworkApprovalScope scope, List<RoleOption> roles) {

    public record RoleOption(UUID id, String name) {
    }
}
