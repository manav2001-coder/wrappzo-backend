package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignOrderVendorRequest(@NotNull UUID vendorId) {
}
