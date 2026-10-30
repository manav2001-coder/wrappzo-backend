package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * One row of the generic employee-roster template. orgUnitPath is broad-to-narrow
 * (e.g. ["HOSPIAID", "EAST"] for Division then Zone) -- at least the top level is
 * required; deeper levels can be left off for a company with a shallower hierarchy.
 * reportsToEmail alone stays optional: every company's roster has at least one
 * person at the top with no manager.
 */
public record BulkImportUserRow(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String mobile,
        @NotBlank String designation,
        @NotBlank String role,
        @NotBlank String employeeCode,
        String reportsToEmail,
        @NotEmpty List<@NotBlank String> orgUnitPath) {
}
