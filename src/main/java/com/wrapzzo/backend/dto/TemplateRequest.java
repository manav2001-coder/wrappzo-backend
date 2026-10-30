package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/** Create or fully replace a template. {@code active} left out means active. */
public record TemplateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String description,
        Boolean active,
        @NotNull Set<UUID> accessControlIds,
        @NotNull Set<UUID> referencedTemplateIds) {
}
