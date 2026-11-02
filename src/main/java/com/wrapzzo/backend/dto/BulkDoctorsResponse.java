package com.wrapzzo.backend.dto;

/**
 * What a doctors upload did: {@code created} are new to the company, {@code joined} were already
 * in the directory (added by someone else) and now sit on the caller's list too, {@code unchanged}
 * were on the caller's list already.
 */
public record BulkDoctorsResponse(int created, int joined, int unchanged) {
}
