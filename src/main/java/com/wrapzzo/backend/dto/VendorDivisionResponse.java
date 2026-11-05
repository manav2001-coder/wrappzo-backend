package com.wrapzzo.backend.dto;

import java.util.UUID;

/** A division (top-level org unit) a vendor is mapped to serve, whether or not an order has come from it yet. */
public record VendorDivisionResponse(UUID id, String name) {
}
