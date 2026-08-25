package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AudienceOptionsResponse;
import com.wrapzzo.backend.dto.CampaignResponse;
import com.wrapzzo.backend.dto.CreateCampaignRequest;
import com.wrapzzo.backend.dto.UpdateCampaignAudienceRequest;
import com.wrapzzo.backend.dto.UpdateCampaignFieldsRequest;
import com.wrapzzo.backend.dto.UpdateCampaignRequest;
import com.wrapzzo.backend.service.CampaignService;
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

/** Company-side campaign management -- PRD Part 2.5. Scoped to the caller's own company. */
@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping
    @PreAuthorize("hasAuthority('campaign.manage')")
    public CampaignResponse create(@Valid @RequestBody CreateCampaignRequest request) {
        return campaignService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('campaign.manage', 'campaign.submit', 'campaign.approve')")
    public List<CampaignResponse> list() {
        return campaignService.listForCurrentUser();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('campaign.manage', 'campaign.submit', 'campaign.approve')")
    public CampaignResponse get(@PathVariable UUID id) {
        return campaignService.get(id);
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('campaign.manage')")
    public CampaignResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCampaignRequest request) {
        return campaignService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('campaign.manage')")
    public CampaignResponse activate(@PathVariable UUID id) {
        return campaignService.activate(id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('campaign.manage')")
    public CampaignResponse close(@PathVariable UUID id) {
        return campaignService.close(id);
    }

    @PutMapping("/{id}/fields")
    @PreAuthorize("hasAuthority('campaign.manage')")
    public CampaignResponse replaceFields(@PathVariable UUID id, @Valid @RequestBody UpdateCampaignFieldsRequest request) {
        return campaignService.replaceFields(id, request.fields());
    }

    @PutMapping("/{id}/audience")
    @PreAuthorize("hasAuthority('campaign.manage')")
    public CampaignResponse replaceAudience(@PathVariable UUID id, @Valid @RequestBody UpdateCampaignAudienceRequest request) {
        return campaignService.replaceAudience(id, request.audiences());
    }

    @GetMapping("/audience-options")
    @PreAuthorize("hasAuthority('campaign.manage')")
    public AudienceOptionsResponse audienceOptions() {
        return campaignService.audienceOptions();
    }
}
