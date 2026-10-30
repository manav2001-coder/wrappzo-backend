package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.GiftResponse;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.GiftService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Gift Store -- any authenticated company user, scoped to active gifts from vendors in
 * their own company's roster (company_vendors, V31). No gift.manage required.
 */
@RestController
@RequestMapping("/api/gifts")
@RequiredArgsConstructor
@RequiresPermission(Keys.PRODUCT_VIEW)
public class GiftBrowseController {

    private final GiftService giftService;
    private final UserRepository userRepository;

    @GetMapping("/list")
    public List<GiftResponse> list() {
        return giftService.listActiveForUser(currentUser());
    }

    @GetMapping("/view/{id}")
    public GiftResponse view(@PathVariable UUID id) {
        return giftService.getActiveForUser(id, currentUser());
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }
}
