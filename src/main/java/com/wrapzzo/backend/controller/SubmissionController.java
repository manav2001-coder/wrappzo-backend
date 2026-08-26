package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.CreateSubmissionRequest;
import com.wrapzzo.backend.dto.PresignSubmissionAttachmentRequest;
import com.wrapzzo.backend.dto.PresignSubmissionAttachmentResponse;
import com.wrapzzo.backend.dto.RejectSubmissionRequest;
import com.wrapzzo.backend.dto.RequestPhotoLinkResponse;
import com.wrapzzo.backend.dto.SubmissionResponse;
import com.wrapzzo.backend.service.SubmissionService;
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

/** Recipient entries submitted against a Campaign, and their approval workflow -- PRD 2.6/2.7. */
@RestController
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/api/campaigns/{campaignId}/submissions/presignAttachment")
    @PreAuthorize("hasAuthority('campaign.submit')")
    public PresignSubmissionAttachmentResponse presignAttachment(
            @PathVariable UUID campaignId, @Valid @RequestBody PresignSubmissionAttachmentRequest request) {
        return submissionService.presignAttachment(campaignId, request.contentType());
    }

    @PostMapping("/api/campaigns/{campaignId}/submissions")
    @PreAuthorize("hasAuthority('campaign.submit')")
    public SubmissionResponse create(@PathVariable UUID campaignId, @Valid @RequestBody CreateSubmissionRequest request) {
        return submissionService.create(campaignId, request);
    }

    @GetMapping("/api/campaigns/{campaignId}/submissions")
    @PreAuthorize("hasAnyAuthority('campaign.manage', 'campaign.approve')")
    public List<SubmissionResponse> listForCampaign(@PathVariable UUID campaignId) {
        return submissionService.listForCampaign(campaignId);
    }

    @PostMapping("/api/submissions/{id}/resubmit")
    @PreAuthorize("hasAuthority('campaign.submit')")
    public SubmissionResponse resubmit(@PathVariable UUID id, @Valid @RequestBody CreateSubmissionRequest request) {
        return submissionService.resubmit(id, request);
    }

    @GetMapping("/api/submissions/mine")
    @PreAuthorize("hasAuthority('campaign.submit')")
    public List<SubmissionResponse> mine() {
        return submissionService.listMine();
    }

    @PostMapping("/api/submissions/{id}/requestPhotoLink")
    @PreAuthorize("hasAuthority('campaign.submit')")
    public RequestPhotoLinkResponse requestPhotoLink(@PathVariable UUID id) {
        return new RequestPhotoLinkResponse(submissionService.requestPhotoLinkAgain(id));
    }

    @GetMapping("/api/submissions/pending-approval")
    @PreAuthorize("hasAnyAuthority('campaign.approve', 'campaign.manage')")
    public List<SubmissionResponse> pendingApproval() {
        return submissionService.listPendingApproval();
    }

    @PostMapping("/api/submissions/{id}/approve")
    @PreAuthorize("hasAnyAuthority('campaign.approve', 'campaign.manage')")
    public SubmissionResponse approve(@PathVariable UUID id) {
        return submissionService.approve(id);
    }

    @PostMapping("/api/submissions/{id}/reject")
    @PreAuthorize("hasAnyAuthority('campaign.approve', 'campaign.manage')")
    public SubmissionResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectSubmissionRequest request) {
        return submissionService.reject(id, request.reason());
    }
}
