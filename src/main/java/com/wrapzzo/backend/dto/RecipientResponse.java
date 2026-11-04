package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Recipient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RecipientResponse(
        UUID id,
        String name,
        String phone,
        /** Null only for a doctor that predates pcodes. */
        String pcode,
        String email,
        String address,
        LocalDate dateOfBirth,
        DoctorDetails doctor,
        String createdByName,
        Instant createdAt,
        /** On the caller's own list (they added the doctor or are mapped to them) -- the only doctors they can remove. */
        boolean inMyList) {

    public static RecipientResponse from(Recipient r) {
        return from(r, false);
    }

    public static RecipientResponse from(Recipient r, boolean inMyList) {
        return new RecipientResponse(
                r.getId(),
                r.getName(),
                r.getPhone(),
                r.getPcode(),
                r.getEmail(),
                r.getAddress(),
                r.getDateOfBirth(),
                new DoctorDetails(r.getQualification(), r.getRegistrationNumber(), r.getSpeciality(), r.getClinicName()),
                r.getCreatedBy() != null ? r.getCreatedBy().getName() : null,
                r.getCreatedAt(),
                inMyList);
    }
}
