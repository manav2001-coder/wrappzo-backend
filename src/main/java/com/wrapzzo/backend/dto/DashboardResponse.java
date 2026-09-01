package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.CampaignStatus;
import com.wrapzzo.backend.model.OrderStatus;
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
        List<ActivityItem> recentActivity,
        List<OrderTimelineItem> recentOrders) {

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

    /**
     * One order's place in the Processing -> Production Queue -> Dispatched -> Delivered
     * pipeline, for the Dashboard's order timeline. status is the current stage; history is
     * every stage it has actually passed through with a real timestamp (see
     * OrderStatusHistory), oldest first.
     */
    public record OrderTimelineItem(
            UUID id,
            String displayId,
            String giftName,
            /** Null if the gift has no image, or the order has no gift (a rare/legacy case). */
            String giftImageUrl,
            String recipientName,
            String requesterName,
            OrderStatus status,
            String source,
            Instant createdAt,
            List<StageEvent> history) {
    }

    /** One row of an order's history: the stage it moved to, when, and by whom (null for a vendor-driven change -- vendors aren't Users). */
    public record StageEvent(OrderStatus status, Instant at, String changedByName) {
    }
}
