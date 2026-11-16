package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AttachProfilePhotoRequest;
import com.wrapzzo.backend.dto.ChangePasswordRequest;
import com.wrapzzo.backend.dto.PresignLogoUploadResponse;
import com.wrapzzo.backend.dto.PresignProfilePhotoRequest;
import com.wrapzzo.backend.dto.VendorProfileResponse;
import com.wrapzzo.backend.dto.TokenResponse;
import com.wrapzzo.backend.dto.UpdatePhoneRequest;
import com.wrapzzo.backend.security.VendorEndpoint;
import com.wrapzzo.backend.service.VendorProfileService;
import com.wrapzzo.backend.service.TokenPair;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A vendor's own profile: gated by the vendor login, and it can only ever touch the signed-in vendor. */
@RestController
@RequestMapping("/api/vendor/profile")
@RequiredArgsConstructor
@VendorEndpoint
public class VendorProfileController {

    private final VendorProfileService vendorProfileService;

    @GetMapping
    public VendorProfileResponse get() {
        return vendorProfileService.get();
    }

    @PutMapping("/phone")
    public VendorProfileResponse updatePhone(@Valid @RequestBody UpdatePhoneRequest request) {
        return vendorProfileService.updatePhone(request.phone());
    }

    @PostMapping("/photo/presign")
    public PresignLogoUploadResponse presignPhoto(@Valid @RequestBody PresignProfilePhotoRequest request) {
        return vendorProfileService.presignPhoto(request.contentType());
    }

    @PutMapping("/photo")
    public VendorProfileResponse attachPhoto(@Valid @RequestBody AttachProfilePhotoRequest request) {
        return vendorProfileService.attachPhoto(request.key());
    }

    @DeleteMapping("/photo")
    public VendorProfileResponse removePhoto() {
        return vendorProfileService.removePhoto();
    }

    /** Returns the new session for this device; every other device is signed out. */
    @PostMapping("/password")
    public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        TokenPair tokens = vendorProfileService.changePassword(request.currentPassword(), request.newPassword(), request.confirmPassword());
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.accessTokenExpiresInSeconds());
    }
}
