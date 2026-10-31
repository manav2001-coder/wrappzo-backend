package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create or edit a role. What it can do comes from the templates mapped to it (Templates to Role), not from here. */
public record RoleRequest(@NotBlank @Size(max = 200) String name, @Size(max = 500) String description, Boolean active) {
}
