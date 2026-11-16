package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AttachProfilePhotoRequest;
import com.wrapzzo.backend.dto.ChangePasswordRequest;
import com.wrapzzo.backend.dto.PresignLogoUploadResponse;
import com.wrapzzo.backend.dto.PresignProfilePhotoRequest;
import com.wrapzzo.backend.dto.ProfileResponse;
import com.wrapzzo.backend.dto.TokenResponse;
import com.wrapzzo.backend.dto.UpdatePhoneRequest;
import com.wrapzzo.backend.security.SignedInUser;
import com.wrapzzo.backend.service.ProfileService;
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

/** The signed-in person's own profile: for anyone, so it needs no permission key -- it can only ever touch the caller. */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@SignedInUser
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ProfileResponse get() {
        return profileService.get();
    }

    @PutMapping("/phone")
    public ProfileResponse updatePhone(@Valid @RequestBody UpdatePhoneRequest request) {
        return profileService.updatePhone(request.phone());
    }

    @PostMapping("/photo/presign")
    public PresignLogoUploadResponse presignPhoto(@Valid @RequestBody PresignProfilePhotoRequest request) {
        return profileService.presignPhoto(request.contentType());
    }

    @PutMapping("/photo")
    public ProfileResponse attachPhoto(@Valid @RequestBody AttachProfilePhotoRequest request) {
        return profileService.attachPhoto(request.key());
    }

    @DeleteMapping("/photo")
    public ProfileResponse removePhoto() {
        return profileService.removePhoto();
    }

    /** Returns the new session for this device; every other device is signed out. */
    @PostMapping("/password")
    public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        TokenPair tokens = profileService.changePassword(request.currentPassword(), request.newPassword(), request.confirmPassword());
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.accessTokenExpiresInSeconds());
    }
}
