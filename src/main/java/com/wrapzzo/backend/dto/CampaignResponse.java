package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Campaign;
import com.wrapzzo.backend.model.CampaignPurpose;
import com.wrapzzo.backend.model.CampaignRecipientCategory;
import com.wrapzzo.backend.model.CampaignStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignResponse(
        UUID id,
        String displayId,
        String name,
        CampaignPurpose purpose,
        CampaignRecipientCategory recipientCategory,
        List<GiftSummary> gifts,
        String instructions,
        Instant submissionDeadline,
        CampaignStatus status,
        List<CampaignFieldResponse> fields,
        List<CampaignAudienceResponse> audiences,
        long submissionCount,
        long pendingApprovalCount,
        long orderCount,
        Instant createdAt) {

    public static CampaignResponse from(
            Campaign campaign,
            List<CampaignFieldResponse> fields,
            List<CampaignAudienceResponse> audiences,
            long submissionCount,
            long pendingApprovalCount,
            long orderCount) {
        return new CampaignResponse(
                campaign.getId(),
                "CMP-" + campaign.getCampaignNo(),
                campaign.getName(),
                campaign.getPurpose(),
                campaign.getRecipientCategory(),
                campaign.getGifts().stream().map(GiftSummary::from).toList(),
                campaign.getInstructions(),
                campaign.getSubmissionDeadline(),
                campaign.getStatus(),
                fields,
                audiences,
                submissionCount,
                pendingApprovalCount,
                orderCount,
                campaign.getCreatedAt());
    }
}
