package com.wrapzzo.backend.dto;

import java.util.UUID;

/** A vendor a company admin may map -- name only, nothing commercial. */
public record AvailableVendorResponse(UUID id, String name) {
}
