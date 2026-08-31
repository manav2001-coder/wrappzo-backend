package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.PresignQuickGiftPhotoRequest;
import com.wrapzzo.backend.dto.PresignQuickGiftPhotoResponse;
import com.wrapzzo.backend.dto.QuickGiftRequestResponse;
import com.wrapzzo.backend.dto.RejectQuickGiftRequest;
import com.wrapzzo.backend.dto.RequestPhotoLinkResponse;
import com.wrapzzo.backend.service.QuickGiftService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quick Gift (PRD Part 2, quick path) -- requesting is Company Admin and Member alike; approving is Company Admin only. */
@RestController
@RequestMapping("/api/quick-gifts")
@RequiredArgsConstructor
public class QuickGiftController {

    private final QuickGiftService quickGiftService;

    @PostMapping("/presignPhoto")
    @PreAuthorize("hasAuthority('quickgift.create')")
    public PresignQuickGiftPhotoResponse presignPhoto(@Valid @RequestBody PresignQuickGiftPhotoRequest request) {
        return quickGiftService.presignPhoto(request.contentType());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('quickgift.create')")
    public QuickGiftRequestResponse create(@Valid @RequestBody CreateQuickGiftRequest request) {
        return quickGiftService.create(request);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('quickgift.create')")
    public List<QuickGiftRequestResponse> mine() {
        return quickGiftService.listMine();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('quickgift.create')")
    public QuickGiftRequestResponse get(@PathVariable UUID id) {
        return quickGiftService.get(id);
    }

    @PostMapping("/{id}/requestPhotoLink")
    @PreAuthorize("hasAuthority('quickgift.create')")
    public RequestPhotoLinkResponse requestPhotoLink(@PathVariable UUID id) {
        return new RequestPhotoLinkResponse(quickGiftService.requestPhotoLinkAgain(id));
    }

    @GetMapping("/pending-approval")
    @PreAuthorize("hasAnyAuthority('campaign.approve', 'campaign.manage')")
    public List<QuickGiftRequestResponse> pendingApproval() {
        return quickGiftService.listPendingApproval();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('campaign.approve', 'campaign.manage')")
    public QuickGiftRequestResponse approve(@PathVariable UUID id) {
        return quickGiftService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('campaign.approve', 'campaign.manage')")
    public QuickGiftRequestResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectQuickGiftRequest request) {
        return quickGiftService.reject(id, request.reason());
    }
}
