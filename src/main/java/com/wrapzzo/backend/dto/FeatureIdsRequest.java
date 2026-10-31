package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

public record FeatureIdsRequest(@NotEmpty Set<UUID> featureIds) {
}
