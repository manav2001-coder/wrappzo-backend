package com.wrapzzo.backend.service;

import java.util.UUID;

/** The customer decided on a proof the vendor is waiting on: sent back for changes, or finally approved. */
public record VendorProofDecidedEvent(UUID designId, boolean approved) {
}
