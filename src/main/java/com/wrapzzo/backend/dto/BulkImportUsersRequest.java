package com.wrapzzo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkImportUsersRequest(@NotEmpty @Valid List<BulkImportUserRow> rows) {
}
