package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/** Create or edit a role. On create {@code templateIds} (optional) are the templates it starts with; on edit it is ignored (templates have their own endpoint). */
public record RoleRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String description,
        Boolean active,
        Set<UUID> templateIds) {
}
