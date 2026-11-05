package com.wrapzzo.backend.service;

import java.util.UUID;

/** An order was (re)assigned to a vendor; the vendor -- and the one it was taken from, if any -- are emailed after commit. */
public record VendorOrderAssignedEvent(UUID orderId, UUID vendorId, UUID previousVendorId) {
}
