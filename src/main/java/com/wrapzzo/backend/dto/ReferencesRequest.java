package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

public record ReferencesRequest(@NotEmpty Set<UUID> referencedTemplateIds) {
}
