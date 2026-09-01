package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.CampaignStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Home-screen overview (PRD 4.7) -- company-wide for a Company Admin (companyWide = true),
 * or scoped to just the viewer's own campaigns/orders/Quick Gift requests for a Member.
 */
public record DashboardResponse(
        String companyName,
        boolean companyWide,
        long campaignsRunning,
        long giftsInProgress,
        long dispatched,
        long delivered,
        long submissionsPending,
        long quickGiftsThisMonth,
        long quickGiftsPending,
        /** Sum of totalPrice for DELIVERED orders whose delivery (updatedAt) falls in the current calendar month. */
        BigDecimal monthlySpend,
        /** Order counts by source -- "CAMPAIGN" / "QUICK_GIFT" / "BULK_GIFT". */
        Map<String, Long> orderSourceBreakdown,
        /** Quick Gift request counts by status -- "SUBMITTED" / "APPROVED" / "REJECTED". */
        Map<String, Long> quickGiftStatusBreakdown,
        /** Campaign submission counts by status -- "PENDING" (submitted/resubmitted) / "APPROVED" / "REJECTED". */
        Map<String, Long> submissionStatusBreakdown,
        /** Last 8 weeks of activity, oldest first -- for a trend chart. */
        List<TrendPoint> activityTrend,
        List<TopGift> topGifts,
        List<TopCampaign> topCampaigns,
        List<TopDepartment> topDepartments,
        List<UpcomingDeadline> upcomingDeadlines,
        List<RecentCampaign> recentCampaigns,
        List<ActivityItem> recentActivity) {

    public record TopGift(String giftName, long orderCount) {
    }

    public record TopCampaign(UUID campaignId, String campaignName, long orderCount) {
    }

    public record TopDepartment(String departmentName, long orderCount) {
    }

    public record UpcomingDeadline(UUID campaignId, String displayId, String campaignName, Instant deadline) {
    }

    public record RecentCampaign(
            UUID id,
            String displayId,
            String name,
            CampaignStatus status,
            int fieldCount,
            Instant submissionDeadline,
            String ownerName,
            Instant createdAt) {
    }

    /**
     * A synthesized activity feed -- e.g. "Sharad created campaign Product Launch" -- derived
     * live from campaigns/submissions/orders/Quick Gift requests, not a persisted audit log.
     * Exactly one of campaignId/quickGiftId is set (and the item clickable) depending on
     * which kind of activity this is; both are null for order-dispatch/delivery events with
     * no campaign link.
     */
    public record ActivityItem(String actorName, String verb, String subject, Instant at, UUID campaignId, UUID quickGiftId) {
    }

    /**
     * One week's worth of campaign-submission and Quick-Gift-request volume, for a trend chart.
     * weekStart (ISO date, Monday) is the drilldown key for dimension "week" -- weekLabel alone
     * isn't parseable back to a date range.
     */
    public record TrendPoint(String weekLabel, String weekStart, long campaignSubmissions, long quickGiftRequests) {
    }
}
