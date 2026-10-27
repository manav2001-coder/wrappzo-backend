package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
        UUID orgUnitId,
        String orgUnitName,
        /** Units the user covers beyond their own -- what they see widens to these subtrees too. */
        List<OrgUnitRef> extraOrgUnits,
        UUID roleId,
        String roleName,
        Instant createdAt) {

    public record OrgUnitRef(UUID id, String name) {
    }

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
                user.getOrgUnit() != null ? user.getOrgUnit().getId() : null,
                user.getOrgUnit() != null ? user.getOrgUnit().getName() : null,
                user.getExtraOrgUnits().stream()
                        .map(unit -> new OrgUnitRef(unit.getId(), unit.getName()))
                        .sorted(Comparator.comparing(OrgUnitRef::name))
                        .toList(),
                user.getRole().getId(),
                user.getRole().getName(),
                user.getCreatedAt());
    }
}
