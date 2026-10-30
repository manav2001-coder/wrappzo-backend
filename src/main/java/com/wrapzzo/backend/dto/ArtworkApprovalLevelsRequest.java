package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ArtworkApprovalLevelsRequest(@NotNull @Min(1) @Max(5) Integer levels) {
}
