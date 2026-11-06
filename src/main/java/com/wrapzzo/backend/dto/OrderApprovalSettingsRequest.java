package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.ArtworkApprovalScope;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Switch order approval on or off and say who approves: a role, found per scope. The role may be left out while it is off. */
public record OrderApprovalSettingsRequest(@NotNull Boolean enabled, UUID roleId, @NotNull ArtworkApprovalScope scope) {
}
