package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ChangeUserRoleRequest(@NotNull UUID roleId) {
}
