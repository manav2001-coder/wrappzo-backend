package com.wrapzzo.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The underlying records behind one clicked chart datapoint -- e.g. every Quick Gift request currently "Submitted". */
public record DashboardDrilldownResponse(String title, List<Row> rows) {

    /** campaignId is set (and the row clickable) whenever it traces back to a campaign.
     * amount is set only where an order's price is meaningful (order/monthly-spend rows) -- null elsewhere. */
    public record Row(String displayId, String title, String subtitle, String status, Instant at, UUID campaignId, BigDecimal amount) {
    }
}
