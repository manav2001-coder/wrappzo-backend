package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.PresignLogoUploadResponse;
import com.wrapzzo.backend.dto.VendorProfileResponse;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.VendorRefreshTokenRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.CurrentVendor;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A vendor's own profile, the counterpart of {@link ProfileService} for the separate vendor login: they change their
 * contact phone, their logo and their password. Their name, email, commission and markup are set by Wrappzo, because they
 * decide pricing. The vendor id always comes from the session.
 */
@Service
@RequiredArgsConstructor
public class VendorProfileService {

    private static final String PHOTO_FOLDER = "vendors";

    private final VendorRepository vendorRepository;
    private final VendorRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final VendorAuthService vendorAuthService;
    private final ProfilePhotoStore photoStore;

    @Transactional(readOnly = true)
    public VendorProfileResponse get() {
        return toResponse(me());
    }

    @Transactional
    public VendorProfileResponse updatePhone(String phone) {
        Vendor vendor = me();
        vendor.setContactPhone(phone.trim());
        return toResponse(vendor);
    }

    public PresignLogoUploadResponse presignPhoto(String contentType) {
        return photoStore.presign(PHOTO_FOLDER, CurrentVendor.id(), contentType);
    }

    @Transactional
    public VendorProfileResponse attachPhoto(String key) {
        Vendor vendor = me();
        photoStore.requireUsable(PHOTO_FOLDER, vendor.getId(), key);
        String previous = vendor.getPhotoKey();
        vendor.setPhotoKey(key);
        if (previous != null && !previous.equals(key)) {
            photoStore.delete(previous);
        }
        return toResponse(vendor);
    }

    @Transactional
    public VendorProfileResponse removePhoto() {
        Vendor vendor = me();
        photoStore.delete(vendor.getPhotoKey());
        vendor.setPhotoKey(null);
        return toResponse(vendor);
    }

    /** As for a person: every other device is signed out, and this one gets a fresh session. */
    @Transactional
    public TokenPair changePassword(String currentPassword, String newPassword, String confirmPassword) {
        Vendor vendor = me();
        vendorAuthService.requireCurrentPassword(vendor, currentPassword);
        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordMismatchException();
        }
        PasswordPolicy.require(newPassword, currentPassword);
        String encoded = passwordEncoder.encode(newPassword);
        // The bulk revoke clears what is loaded, so the vendor is read again after it rather than held across it.
        refreshTokenRepository.revokeAllFor(vendor.getId(), Instant.now());
        Vendor fresh = me();
        fresh.setPasswordHash(encoded);
        return vendorAuthService.startSession(fresh);
    }

    private Vendor me() {
        UUID id = CurrentVendor.id();
        return vendorRepository.findById(id).orElseThrow(() -> new IllegalStateException("Authenticated vendor not found: " + id));
    }

    private VendorProfileResponse toResponse(Vendor vendor) {
        return new VendorProfileResponse(
                vendor.getName(),
                vendor.getContactEmail(),
                vendor.getContactPhone(),
                vendor.getVendorNo() != null ? "VID-" + vendor.getVendorNo() : null,
                photoStore.urlFor(vendor.getPhotoKey()));
    }
}
