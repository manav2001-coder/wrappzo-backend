package com.wrapzzo.backend.dto;

import java.util.List;

public record BulkImportRolesResponse(int createdCount, int updatedCount, List<RoleResponse> roles) {
}
