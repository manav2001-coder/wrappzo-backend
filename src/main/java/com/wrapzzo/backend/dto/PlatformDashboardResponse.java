package com.wrapzzo.backend.dto;

import java.util.Map;

/** Platform-wide counts for Wrappzo staff: what the platform serves and where its orders stand. */
public record PlatformDashboardResponse(
        long companies,
        long activeCompanies,
        long vendors,
        long activeVendors,
        long users,
        long gifts,
        long activeGifts,
        long orders,
        Map<String, Long> ordersByStatus) {}
