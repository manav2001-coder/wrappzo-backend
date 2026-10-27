package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Replace-all: the full set of extra units the user should cover afterwards (empty clears them). */
public record SetExtraOrgUnitsRequest(@NotNull @Size(max = 200) List<@NotNull UUID> orgUnitIds) {
}
