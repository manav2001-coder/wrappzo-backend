package com.wrapzzo.backend.dto;

/** A vendor's own profile: the contact phone and logo are theirs to change; the name, email, commission and markup are Wrappzo's. */
public record VendorProfileResponse(
        String name,
        String contactEmail,
        String contactPhone,
        String vendorNumber,
        String photoUrl) {
}
