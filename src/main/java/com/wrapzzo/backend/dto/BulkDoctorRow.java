package com.wrapzzo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** One row of the doctors sheet a user uploads to the Doctors screen: the same fields as adding one doctor by hand. */
public record BulkDoctorRow(
        @NotBlank String name,
        @NotBlank String phone,
        String email,
        String address,
        LocalDate dateOfBirth,
        @Valid DoctorDetails doctor) {
}
