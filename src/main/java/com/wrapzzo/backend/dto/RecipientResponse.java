package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Recipient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RecipientResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String address,
        LocalDate dateOfBirth,
        DoctorDetails doctor,
        String createdByName,
        Instant createdAt) {

    public static RecipientResponse from(Recipient r) {
        return new RecipientResponse(
                r.getId(),
                r.getName(),
                r.getPhone(),
                r.getEmail(),
                r.getAddress(),
                r.getDateOfBirth(),
                new DoctorDetails(r.getQualification(), r.getRegistrationNumber(), r.getSpeciality(), r.getClinicName()),
                r.getCreatedBy() != null ? r.getCreatedBy().getName() : null,
                r.getCreatedAt());
    }
}
