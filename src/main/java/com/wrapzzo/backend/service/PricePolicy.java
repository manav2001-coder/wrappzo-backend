package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who may see what a gift costs: the company admin (price.view) -- nobody who raises orders or approves them, HO included.
 * Every response that carries a price asks here and leaves it out when the answer is no, so it never leaves the server.
 */
@Component
@RequiredArgsConstructor
public class PricePolicy {

    private final UserRepository userRepository;
    private final AccessControlCacheService accessControlCacheService;

    public boolean canSee() {
        User user = userRepository.findById(CurrentUser.id()).orElse(null);
        return user != null && accessControlCacheService.resolveEffectiveKeys(user).contains(Permission.PRICE_VIEW.key());
    }
}
