package com.wrapzzo.backend.dto;

/**
 * A person's own profile: what they may change (mobile, picture) and what they only see (the rest is set by their admin,
 * because it decides who approves what and who sees whose records).
 */
public record ProfileResponse(
        String name,
        String email,
        String mobile,
        String designation,
        String employeeCode,
        String roleName,
        String orgUnitName,
        String managerName,
        String companyName,
        String photoUrl) {
}
