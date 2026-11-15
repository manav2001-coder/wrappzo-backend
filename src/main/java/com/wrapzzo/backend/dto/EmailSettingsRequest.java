package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;

public record EmailSettingsRequest(@NotNull Boolean workflowEmailsEnabled) {
}
