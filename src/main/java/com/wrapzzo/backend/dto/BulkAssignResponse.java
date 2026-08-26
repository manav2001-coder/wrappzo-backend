package com.wrapzzo.backend.dto;

import java.util.List;

/** Result of a campaign-level bulk vendor assignment -- skippedCount is always 0 for a manual assign-to-vendor (any vendor id works), non-zero for auto-assign when some orders had no active vendor to fall back to. */
public record BulkAssignResponse(int assignedCount, int skippedCount, List<OrderResponse> orders) {}
