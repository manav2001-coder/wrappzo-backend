package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.CreateSubmissionRequest;
import com.wrapzzo.backend.dto.PresignSubmissionAttachmentRequest;
import com.wrapzzo.backend.dto.PresignSubmissionAttachmentResponse;
import com.wrapzzo.backend.dto.RequestPhotoLinkResponse;
import com.wrapzzo.backend.dto.SubmissionResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.SubmissionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Recipient entries submitted against a Campaign -- no approval gate, an Order is created the moment one is submitted. */
@RestController
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/api/campaigns/{campaignId}/submissions/presignAttachment")
    @RequiresPermission(Keys.CAMPAIGN_ENTRY_SUBMIT)
    public PresignSubmissionAttachmentResponse presignAttachment(
            @PathVariable UUID campaignId, @Valid @RequestBody PresignSubmissionAttachmentRequest request) {
        return submissionService.presignAttachment(campaignId, request.contentType());
    }

    @PostMapping("/api/campaigns/{campaignId}/submissions")
    @RequiresPermission(Keys.CAMPAIGN_ENTRY_SUBMIT)
    public SubmissionResponse create(@PathVariable UUID campaignId, @Valid @RequestBody CreateSubmissionRequest request) {
        return submissionService.create(campaignId, request);
    }

    @GetMapping("/api/campaigns/{campaignId}/submissions")
    @RequiresPermission(Keys.CAMPAIGN_ENTRY_VIEW)
    public List<SubmissionResponse> listForCampaign(@PathVariable UUID campaignId) {
        return submissionService.listForCampaign(campaignId);
    }

    @GetMapping("/api/submissions/mine")
    @RequiresPermission(Keys.CAMPAIGN_ENTRY_SUBMIT)
    public List<SubmissionResponse> mine() {
        return submissionService.listMine();
    }

    @PostMapping("/api/submissions/{id}/requestPhotoLink")
    @RequiresPermission(Keys.CAMPAIGN_ENTRY_SUBMIT)
    public RequestPhotoLinkResponse requestPhotoLink(@PathVariable UUID id) {
        return new RequestPhotoLinkResponse(submissionService.requestPhotoLinkAgain(id));
    }
}
