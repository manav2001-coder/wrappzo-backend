package com.wrapzzo.backend.dto;

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
        List<TeamMember> team) {

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
