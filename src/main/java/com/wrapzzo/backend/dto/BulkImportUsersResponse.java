package com.wrapzzo.backend.dto;

import java.util.List;

public record BulkImportUsersResponse(int createdCount, int updatedCount, List<UserResponse> users) {
}
