package com.wrapzzo.backend.dto;

import java.util.List;
import java.util.UUID;

public record BulkAssignUserTemplatesResponse(
        int totalUsersProcessed,
        int successfulUsers,
        int failedUsers,
        int totalAssignmentsCreated,
        int totalAssignmentsSkipped,
        List<UserResult> userResults) {

    /** {@code status} is "success" or "failed"; a failure carries its {@code error}. */
    public record UserResult(UUID userId, String userName, int added, int skipped, String status, String error) {
    }
}
