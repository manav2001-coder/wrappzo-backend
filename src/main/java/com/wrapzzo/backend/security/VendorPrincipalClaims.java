package com.wrapzzo.backend.security;

import java.util.UUID;

/** No roleId/companyId -- a vendor's capability set is fixed, not configurable. See Vendor's own doc comment. */
public record VendorPrincipalClaims(UUID vendorId) implements PrincipalClaims {
}
