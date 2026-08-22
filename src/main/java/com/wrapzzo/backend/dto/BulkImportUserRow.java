package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * One row of the generic employee-roster template. orgUnitPath is broad-to-narrow
 * (e.g. ["HOSPIAID", "EAST"] for Division then Zone) -- as many or as few levels
 * as the client's sheet has; a company with no named hierarchy just leaves it empty.
 */
public record BulkImportUserRow(
        @NotBlank String name,
        @NotBlank @Email String email,
        String mobile,
        String designation,
        String employeeCode,
        String reportsToEmail,
        List<String> orgUnitPath) {
}
