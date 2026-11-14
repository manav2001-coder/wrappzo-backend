package com.wrapzzo.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The role-shaped half of the dashboard: figures for a chosen period (with the period before it, for "up/down"), where orders are
 * waiting and for how long, how many of the viewer's doctors have been covered, and -- for anyone who manages people -- one row per
 * person. scope is PERSONAL for someone who sees only their own records, TEAM otherwise.
 */
public record DashboardInsightsResponse(
        String scope,
        String range,
        Instant from,
        Instant to,
        Period current,
        /** Null for "all time", which has nothing before it to compare with. */
        Period previous,
        /** Doctors assigned to the people in view, and how many of them had a PEL requested in the period. */
        Coverage coverage,
        /** In pipeline order: approval, artwork, production, on the way. */
        List<StageAging> stages,
        /** Requests turned down in the period -- the AM sees these as something to look at. */
        long rejectedRequests,
        List<WeekPoint> weekly,
        long teamSize,
        List<TeamMember> team,
        /** Only for someone who may see prices (price.view); null for everyone else, so no amount ever leaves the server. */
        Spend spend,
        /** Only for someone who may see which vendor makes an order (vendor_identity.view); null for everyone else. */
        List<VendorScore> vendors) {

    /** previousTotal is null for "all time". costPerPel is the total over the PELs ordered in the period (null when there are none). */
    public record Spend(BigDecimal total, BigDecimal previousTotal, BigDecimal costPerPel, List<SpendLine> byGift, List<SpendLine> byUnit) {
    }

    public record SpendLine(String name, BigDecimal amount) {
    }

    /** How one vendor did on the orders placed with them in the period. Days are null when no order got that far. */
    public record VendorScore(
            UUID vendorId,
            String name,
            long orders,
            long delivered,
            long open,
            /** Open orders more than 10 days old. */
            long slowOpen,
            Double avgDaysToDispatch,
            Double avgDaysToDeliver,
            /** Artwork versions the vendor uploaded, and how many were sent back. */
            long designs,
            long designsRejected) {
    }

    /** PELs asked for, delivered, and doctors asked for, plus the average days from request to delivery of what was delivered. */
    public record Period(long requested, long served, long doctorsRequested, long doctorsServed, Double avgDaysToDeliver) {
    }

    public record Coverage(long assigned, long covered) {
    }

    /** stuck = how many have waited longer than thresholdDays in this stage; oldestDays = the longest wait. */
    public record StageAging(String stage, long count, long stuck, long oldestDays, int thresholdDays) {
    }

    public record WeekPoint(String weekLabel, String weekStart, long requested, long delivered) {
    }

    public record TeamMember(
            UUID userId,
            String name,
            String unitName,
            long requested,
            long served,
            long doctorsAssigned,
            long doctorsCovered,
            long inPipeline,
            Instant lastActivityAt) {
    }
}
