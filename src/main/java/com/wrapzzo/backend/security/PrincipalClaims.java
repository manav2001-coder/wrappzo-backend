package com.wrapzzo.backend.security;

/** What we trust from a validated access token — deliberately just identity, never a permission snapshot. */
public sealed interface PrincipalClaims permits UserPrincipalClaims, VendorPrincipalClaims {
}
