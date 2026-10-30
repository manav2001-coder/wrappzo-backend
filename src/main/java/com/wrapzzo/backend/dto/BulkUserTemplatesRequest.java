package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record BulkUserTemplatesRequest(@NotEmpty @Size(max = 500) Set<UUID> userIds, @NotEmpty Set<UUID> templateIds) {
}
