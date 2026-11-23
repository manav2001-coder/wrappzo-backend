package com.wrapzzo.backend.dto;

/** How many items wait on the caller -- the number a tab badge shows. */
public record PendingCountResponse(long total) {
}
