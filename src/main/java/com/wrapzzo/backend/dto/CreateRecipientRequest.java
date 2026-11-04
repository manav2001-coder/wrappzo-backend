package com.wrapzzo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateRecipientRequest(
        @NotBlank String name,
        @NotBlank String phone,
        /** The doctor's unique identifier -- required. */
        @NotBlank @Size(max = 50) String pcode,
        String email,
        String address,
        LocalDate dateOfBirth,
        @Valid DoctorDetails doctor) {
}
