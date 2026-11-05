package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who may know which vendor (printer) makes an order: the company admin (company_vendor.view) and Wrappzo's own staff
 * (platform_vendor.view) -- nobody who raises orders or approves artwork, HO included. Every response that could name a
 * vendor asks here and leaves the name and id out when the answer is no, so it never leaves the server.
 */
@Component
@RequiredArgsConstructor
public class VendorIdentityPolicy {

    private final UserRepository userRepository;
    private final AccessControlCacheService accessControlCacheService;

    public boolean canSee() {
        User user = userRepository.findById(CurrentUser.id()).orElse(null);
        if (user == null) {
            return false;
        }
        var keys = accessControlCacheService.resolveEffectiveKeys(user);
        return keys.contains(Permission.COMPANY_VENDOR_VIEW.key()) || keys.contains(Permission.PLATFORM_VENDOR_VIEW.key());
    }
}
