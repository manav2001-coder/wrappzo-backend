package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String mobile,
        String designation,
        String employeeCode,
        UserStatus status,
        String reportsToName,
        String orgUnitName,
        UUID roleId,
        String roleName,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getMobile(),
                user.getDesignation(),
                user.getEmployeeCode(),
                user.getStatus(),
                user.getReportsTo() != null ? user.getReportsTo().getName() : null,
                user.getOrgUnit() != null ? user.getOrgUnit().getName() : null,
                user.getRole().getId(),
                user.getRole().getName(),
                user.getCreatedAt());
    }
}
