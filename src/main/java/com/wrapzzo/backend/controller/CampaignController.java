package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AudienceOptionsResponse;
import com.wrapzzo.backend.dto.CampaignResponse;
import com.wrapzzo.backend.dto.CreateCampaignRequest;
import com.wrapzzo.backend.dto.UpdateCampaignAudienceRequest;
import com.wrapzzo.backend.dto.UpdateCampaignFieldsRequest;
import com.wrapzzo.backend.dto.UpdateCampaignRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
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
    @RequiresPermission(Keys.CAMPAIGN_CREATE)
    public CampaignResponse create(@Valid @RequestBody CreateCampaignRequest request) {
        return campaignService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Keys.CAMPAIGN_VIEW + "', '" + Keys.CAMPAIGN_ENTRY_SUBMIT + "')")
    public List<CampaignResponse> list() {
        return campaignService.listForCurrentUser();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Keys.CAMPAIGN_VIEW + "', '" + Keys.CAMPAIGN_ENTRY_SUBMIT + "')")
    public CampaignResponse get(@PathVariable UUID id) {
        return campaignService.get(id);
    }

    @PostMapping("/{id}")
    @RequiresPermission(Keys.CAMPAIGN_EDIT)
    public CampaignResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCampaignRequest request) {
        return campaignService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @RequiresPermission(Keys.CAMPAIGN_PUBLISH)
    public CampaignResponse activate(@PathVariable UUID id) {
        return campaignService.activate(id);
    }

    @PostMapping("/{id}/close")
    @RequiresPermission(Keys.CAMPAIGN_PUBLISH)
    public CampaignResponse close(@PathVariable UUID id) {
        return campaignService.close(id);
    }

    @PutMapping("/{id}/fields")
    @RequiresPermission(Keys.CAMPAIGN_EDIT)
    public CampaignResponse replaceFields(@PathVariable UUID id, @Valid @RequestBody UpdateCampaignFieldsRequest request) {
        return campaignService.replaceFields(id, request.fields());
    }

    @PutMapping("/{id}/audience")
    @RequiresPermission(Keys.CAMPAIGN_EDIT)
    public CampaignResponse replaceAudience(@PathVariable UUID id, @Valid @RequestBody UpdateCampaignAudienceRequest request) {
        return campaignService.replaceAudience(id, request.audiences());
    }

    @GetMapping("/audience-options")
    @RequiresPermission(Keys.CAMPAIGN_EDIT)
    public AudienceOptionsResponse audienceOptions() {
        return campaignService.audienceOptions();
    }
}
