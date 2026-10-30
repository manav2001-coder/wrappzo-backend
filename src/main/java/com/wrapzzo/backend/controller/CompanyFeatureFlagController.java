package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.ArtworkApprovalLevelsRequest;
import com.wrapzzo.backend.dto.ArtworkApprovalLevelsResponse;
import com.wrapzzo.backend.dto.CompanyFeatureFlagResponse;
import com.wrapzzo.backend.dto.SetCompanyFeatureFlagRequest;
import com.wrapzzo.backend.service.CompanyFeatureFlagService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-side: which optional workflow features a company has turned on -- gated by company.manage (platform-only, a structural business decision, not self-service). */
@RestController
@RequestMapping("/api/platform/companies/{companyId}/features")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('company.manage')")
public class CompanyFeatureFlagController {

    private final CompanyFeatureFlagService companyFeatureFlagService;

    @GetMapping
    public List<CompanyFeatureFlagResponse> list(@PathVariable UUID companyId) {
        return companyFeatureFlagService.list(companyId);
    }

    @PostMapping
    public List<CompanyFeatureFlagResponse> set(@PathVariable UUID companyId, @Valid @RequestBody SetCompanyFeatureFlagRequest request) {
        return companyFeatureFlagService.setEnabled(companyId, request.flagKey(), request.enabled());
    }

    @GetMapping("/artwork-approval-levels")
    public ArtworkApprovalLevelsResponse artworkApprovalLevels(@PathVariable UUID companyId) {
        return companyFeatureFlagService.getArtworkApprovalLevels(companyId);
    }

    @PutMapping("/artwork-approval-levels")
    public ArtworkApprovalLevelsResponse setArtworkApprovalLevels(@PathVariable UUID companyId, @Valid @RequestBody ArtworkApprovalLevelsRequest request) {
        return companyFeatureFlagService.setArtworkApprovalLevels(companyId, request.levels());
    }
}
