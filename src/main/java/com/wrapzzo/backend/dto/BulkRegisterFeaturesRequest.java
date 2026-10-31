package com.wrapzzo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** The permission keys to register for the company; ones it already has are skipped. */
public record BulkRegisterFeaturesRequest(@NotEmpty @Valid List<Entry> features) {

    public record Entry(@NotBlank String key) {
    }
}
