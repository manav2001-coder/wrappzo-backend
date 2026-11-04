package com.wrapzzo.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The underlying records behind one clicked chart datapoint -- e.g. every Quick Gift request currently "Submitted". */
public record DashboardDrilldownResponse(String title, List<Row> rows) {

    /** campaignId is set (and the row clickable) whenever it traces back to a campaign.
     * quantity is the PELs on the row (an order, or a doctor's total) -- null where a row has none. */
    public record Row(String displayId, String title, String subtitle, String status, Instant at, UUID campaignId, Long quantity) {
    }
}
