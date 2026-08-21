package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.AddVendorRequest;
import com.wrapzzo.backend.dto.AddVendorResponse;
import com.wrapzzo.backend.dto.EditVendorRequest;
import com.wrapzzo.backend.dto.VendorResponse;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.exception.VendorEmailAlreadyExistsException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.security.TokenHasher;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Super Admin-only vendor onboarding/management -- see Vendor's own doc comment for why this is kept separate from User. */
@Service
@RequiredArgsConstructor
public class VendorService {

    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AddVendorResponse add(AddVendorRequest request) {
        if (vendorRepository.findByContactEmail(request.contactEmail()).isPresent()) {
            throw new VendorEmailAlreadyExistsException(request.contactEmail());
        }

        String rawPassword = resolvePassword(request.password(), request.confirmPassword());
        String passwordHash = passwordEncoder.encode(rawPassword);

        Vendor vendor = vendorRepository.save(
                new Vendor(request.name(), request.contactEmail(), request.contactPhone(), passwordHash, currentUser()));

        String temporaryPassword = request.password() == null ? rawPassword : null;
        return new AddVendorResponse(VendorResponse.from(vendor), temporaryPassword);
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

    private Vendor getEntity(UUID id) {
        return vendorRepository.findById(id).orElseThrow(() -> new VendorNotFoundException(id));
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }

    private static String resolvePassword(String password, String confirmPassword) {
        if (password == null && confirmPassword == null) {
            return TokenHasher.generateSecurePassword();
        }
        if (!Objects.equals(password, confirmPassword)) {
            throw new PasswordMismatchException();
        }
        return password;
    }
}
