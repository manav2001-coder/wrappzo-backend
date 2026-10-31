package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One row of the roles sheet: a name, and an optional description. */
public record BulkImportRoleRow(@NotBlank @Size(max = 200) String name, @Size(max = 500) String description) {
}
