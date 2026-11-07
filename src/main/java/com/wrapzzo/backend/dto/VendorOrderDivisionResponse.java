package com.wrapzzo.backend.dto;

import java.util.UUID;

/** A division the vendor has orders from, with how many. */
public record VendorOrderDivisionResponse(UUID id, String name, long count) {
}
