package com.wrapzzo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkDoctorsRequest(@NotEmpty @Size(max = 2000) @Valid List<BulkDoctorRow> rows) {
}
