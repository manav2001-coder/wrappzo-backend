package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/** The full set of templates a role or user should carry. */
public record TemplateIdsRequest(@NotNull Set<UUID> templateIds) {
}
