package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.AddVendorRequest;
import com.wrapzzo.backend.dto.EditVendorRequest;
import com.wrapzzo.backend.dto.VendorResponse;
import com.wrapzzo.backend.exception.EmailDeliveryException;
import com.wrapzzo.backend.exception.VendorEmailAlreadyExistsException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorInviteToken;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorInviteTokenRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Super Admin-only vendor onboarding/management -- see Vendor's own doc comment for why this is kept separate from User. */
@Service
@Slf4j
public class VendorService {

    private final VendorRepository vendorRepository;
    private final VendorInviteTokenRepository vendorInviteTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final long inviteTokenExpiryHours;
    private final String frontendBaseUrl;

    public VendorService(
            VendorRepository vendorRepository,
            VendorInviteTokenRepository vendorInviteTokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            @Value("${vendor.invite-token-expiry-hours}") long inviteTokenExpiryHours,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.vendorRepository = vendorRepository;
        this.vendorInviteTokenRepository = vendorInviteTokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.inviteTokenExpiryHours = inviteTokenExpiryHours;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public VendorResponse add(AddVendorRequest request) {
        if (vendorRepository.findByContactEmail(request.contactEmail()).isPresent()) {
            throw new VendorEmailAlreadyExistsException(request.contactEmail());
        }

        // Unusable placeholder -- a real password is never set here. The vendor can only
        // ever authenticate after setting one themselves via the emailed invite link.
        String placeholderHash = passwordEncoder.encode(TokenHasher.generateOpaqueToken());
        Vendor newVendor = new Vendor(request.name(), request.contactEmail(), request.contactPhone(), placeholderHash, currentUser());
        newVendor.setMarkupFees(request.markupFees());
        newVendor.setCommission(request.commission());
        // saveAndFlush -- vendorNo is DB-generated and only readable after the physical
        // INSERT runs, which plain save() would otherwise defer until transaction commit.
        Vendor vendor = vendorRepository.saveAndFlush(newVendor);

        issueInviteAndSendEmail(vendor);
        return VendorResponse.from(vendor);
    }

    public List<VendorResponse> list() {
        return vendorRepository.findAll().stream().map(VendorResponse::from).toList();
    }

    public VendorResponse view(UUID id) {
        return VendorResponse.from(getEntity(id));
    }

    @Transactional
    public VendorResponse edit(UUID id, EditVendorRequest request) {
        Vendor vendor = getEntity(id);
        vendor.setName(request.name());
        vendor.setContactPhone(request.contactPhone());
        vendor.setMarkupFees(request.markupFees());
        vendor.setCommission(request.commission());
        vendor.setUpdatedBy(currentUser());
        return VendorResponse.from(vendor);
    }

    @Transactional
    public VendorResponse setStatus(UUID id, VendorStatus status) {
        Vendor vendor = getEntity(id);
        vendor.setStatus(status);
        vendor.setUpdatedBy(currentUser());
        return VendorResponse.from(vendor);
    }

    /** For when the original invite email was lost or its link expired before the vendor used it. */
    @Transactional
    public VendorResponse resendInvite(UUID id) {
        Vendor vendor = getEntity(id);
        issueInviteAndSendEmail(vendor);
        return VendorResponse.from(vendor);
    }

    /**
     * The token is always persisted -- the vendor's own onboarding (and the admin's ability
     * to onboard them at all) must not depend on a third-party email provider being up. A
     * failed send is logged and swallowed rather than rolling back the surrounding
     * transaction; the admin can retry via resendInvite() once email is working, same as if
     * the original link had simply expired.
     */
    private void issueInviteAndSendEmail(Vendor vendor) {
        vendorInviteTokenRepository.deleteUnusedByVendorId(vendor.getId());

        String rawToken = TokenHasher.generateOpaqueToken();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(inviteTokenExpiryHours));
        vendorInviteTokenRepository.save(new VendorInviteToken(vendor, TokenHasher.sha256Hex(rawToken), expiresAt));

        String setupUrl = frontendBaseUrl + "/vendor/setup-password?token=" + rawToken;
        try {
            emailSender.send(
                    vendor.getContactEmail(),
                    "Set up your Wrappzo vendor account",
                    """
                    <p>Hi %s,</p>
                    <p>You've been added as a vendor on Wrappzo. Set up your password to get started:</p>
                    <p><a href="%s">Set up your password</a></p>
                    <p>This link expires in %d hours.</p>
                    """
                            .formatted(vendor.getName(), setupUrl, inviteTokenExpiryHours));
        } catch (EmailDeliveryException e) {
            log.warn("Vendor invite email to {} failed to send -- vendor and invite token were still created", vendor.getContactEmail(), e);
        }
    }

    private Vendor getEntity(UUID id) {
        return vendorRepository.findById(id).orElseThrow(() -> new VendorNotFoundException(id));
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }
}
