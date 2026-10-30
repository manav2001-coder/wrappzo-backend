package com.wrapzzo.backend.dto;

import java.util.List;
import java.util.UUID;

public record BulkUserTemplatesResponse(List<UserResult> results) {

    /** {@code status} is "ok" or "failed"; {@code skipped} counts templates the user already had. */
    public record UserResult(UUID userId, String userName, int added, int skipped, String status, String error) {
    }
}
