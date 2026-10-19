package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignCompanyVendorRequest(@NotNull UUID vendorId) {
}
