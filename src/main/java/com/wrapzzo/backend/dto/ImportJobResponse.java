package com.wrapzzo.backend.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where a bulk upload stands. A small sheet is already COMPLETED when the upload returns; a large one is QUEUED or
 * RUNNING and the screen asks again until it is COMPLETED (see {@code counts}: what was created, updated and so on)
 * or FAILED ({@code message}, and the bad rows in {@code errors}). A failed import saved nothing.
 */
public record ImportJobResponse(UUID id, String kind, String status, int totalRows, Map<String, Integer> counts, List<String> errors, String message) {
}
