package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record UpdateDesignationAccessRequest(@NotBlank String designation, @NotNull Set<UUID> accessControlIds) {
}
