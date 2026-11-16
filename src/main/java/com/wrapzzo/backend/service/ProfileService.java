package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.PresignLogoUploadResponse;
import com.wrapzzo.backend.dto.ProfileResponse;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.RefreshTokenRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A signed-in person's own profile. They change their mobile number, their picture and their password -- nothing that
 * decides access (name, email, role, manager, placement), which stays with their admin. Every call acts on the caller
 * only: the id always comes from the session, never from the request.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final String PHOTO_FOLDER = "users";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final ProfilePhotoStore photoStore;

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        return toResponse(me());
    }

    @Transactional
    public ProfileResponse updatePhone(String phone) {
        User user = me();
        user.setMobile(phone.trim());
        return toResponse(user);
    }

    public PresignLogoUploadResponse presignPhoto(String contentType) {
        return photoStore.presign(PHOTO_FOLDER, CurrentUser.id(), contentType);
    }

    @Transactional
    public ProfileResponse attachPhoto(String key) {
        User user = me();
        photoStore.requireUsable(PHOTO_FOLDER, user.getId(), key);
        String previous = user.getPhotoKey();
        user.setPhotoKey(key);
        if (previous != null && !previous.equals(key)) {
            photoStore.delete(previous);
        }
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse removePhoto() {
        User user = me();
        photoStore.delete(user.getPhotoKey());
        user.setPhotoKey(null);
        return toResponse(user);
    }

    /**
     * Changes the password and signs the person out of every other device: all their sessions end, and the device they are
     * on is given a new one so they are not thrown out of what they are doing. (An access token already issued lives out its
     * short life; the refresh that would extend it is gone.)
     */
    @Transactional
    public TokenPair changePassword(String currentPassword, String newPassword, String confirmPassword) {
        User user = me();
        authService.requireCurrentPassword(user, currentPassword);
        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordMismatchException();
        }
        PasswordPolicy.require(newPassword, currentPassword);
        String encoded = passwordEncoder.encode(newPassword);
        // The bulk revoke clears what is loaded, so the person is read again after it rather than held across it.
        refreshTokenRepository.revokeAllFor(user.getId(), Instant.now());
        User fresh = me();
        fresh.setPasswordHash(encoded);
        return authService.startSession(fresh);
    }

    private User me() {
        UUID id = CurrentUser.id();
        return userRepository.findById(id).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + id));
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getName(),
                user.getEmail(),
                user.getMobile(),
                user.getDesignation(),
                user.getEmployeeCode(),
                user.getRole() != null ? user.getRole().getName() : null,
                user.getOrgUnit() != null ? user.getOrgUnit().getName() : null,
                user.getReportsTo() != null ? user.getReportsTo().getName() : null,
                user.getCompany() != null ? user.getCompany().getName() : null,
                photoStore.urlFor(user.getPhotoKey()));
    }
}
