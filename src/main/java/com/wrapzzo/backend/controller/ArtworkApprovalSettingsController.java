package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.ArtworkApprovalStepsRequest;
import com.wrapzzo.backend.dto.ArtworkApprovalStepsResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.ArtworkApprovalSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own company's artwork approval steps: who approves a proof after the AM, in order. */
@RestController
@RequestMapping("/api/artwork-approval/steps")
@RequiredArgsConstructor
public class ArtworkApprovalSettingsController {

    private final ArtworkApprovalSettingsService artworkApprovalSettingsService;

    @GetMapping
    @RequiresPermission(Keys.ARTWORK_APPROVAL_VIEW)
    public ArtworkApprovalStepsResponse get() {
        return artworkApprovalSettingsService.get();
    }

    @PutMapping
    @RequiresPermission(Keys.ARTWORK_APPROVAL_CONFIGURE)
    public ArtworkApprovalStepsResponse set(@Valid @RequestBody ArtworkApprovalStepsRequest request) {
        return artworkApprovalSettingsService.set(request);
    }
}
