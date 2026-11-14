package com.wrapzzo.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The underlying records behind one clicked chart datapoint -- e.g. every Quick Gift request currently "Submitted" -- one page at a time.
 * total is how many records there are (up to the most the dashboard reads for one datapoint); capped says there are more than that.
 */
public record DashboardDrilldownResponse(String title, List<Row> rows, long total, int page, int pageSize, boolean capped) {

    /** The whole set, before it is cut into pages. */
    public DashboardDrilldownResponse(String title, List<Row> rows) {
        this(title, rows, rows.size(), 0, rows.size(), false);
    }

    /** One page of this set (pages count from 0); {@code capped} when the set hit the most the dashboard reads. */
    public DashboardDrilldownResponse pageOf(int page, int pageSize, int cap) {
        int from = (int) Math.min((long) page * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return new DashboardDrilldownResponse(title, rows.subList(from, to), rows.size(), page, pageSize, rows.size() >= cap);
    }

    /** campaignId is set (and the row clickable) whenever it traces back to a campaign.
     * quantity is the PELs on the row (an order, or a doctor's total) -- null where a row has none. */
    public record Row(String displayId, String title, String subtitle, String status, Instant at, UUID campaignId, Long quantity) {
    }
}
