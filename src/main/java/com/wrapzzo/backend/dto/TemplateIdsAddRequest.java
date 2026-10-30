package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

/** Templates to add to another template's includes. */
public record TemplateIdsAddRequest(@NotEmpty Set<UUID> templateIds) {
}
