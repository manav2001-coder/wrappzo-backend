package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * What a personalised prescription pad is printed with, beyond name/phone/address. Optional
 * on every request that carries it -- a non-pharma company never fills it in. The
 * registration number is the directory's dedupe key when present (see RecipientService).
 */
public record DoctorDetails(
        @Size(max = 255) String qualification,
        @Size(max = 100) String registrationNumber,
        @Size(max = 255) String speciality,
        @Size(max = 255) String clinicName) {

    public static final DoctorDetails EMPTY = new DoctorDetails(null, null, null, null);

    /** Blank strings stored as null, so "not given" has one representation. */
    public DoctorDetails normalized() {
        return new DoctorDetails(clean(qualification), clean(registrationNumber), clean(speciality), clean(clinicName));
    }

    public static DoctorDetails orEmpty(DoctorDetails details) {
        return details == null ? EMPTY : details.normalized();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
