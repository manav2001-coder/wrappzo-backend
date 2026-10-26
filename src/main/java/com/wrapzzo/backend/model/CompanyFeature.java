package com.wrapzzo.backend.model;

/**
 * Known per-company feature flags (company_feature_flags.flag_key). Keep this the single
 * source of truth for valid keys -- CompanyFeatureFlagService.list() uses it to show every
 * company a complete, consistent list (including flags they've never touched, defaulted off).
 */
public enum CompanyFeature {
    /** Vendor -> company warehouse -> doctor/MR, two tracked legs instead of one. */
    WAREHOUSE_LOGISTICS("Warehouse Logistics", "Route orders through your own warehouse before final delivery, with both legs tracked."),
    /** An extra artwork sign-off by the company's Marketing/Operations Admin, in front of printing. */
    MARKETING_DESIGN_APPROVAL("Marketing Design Approval", "Require your Marketing/Operations Admin to approve artwork before it goes to print."),
    /** Quick Gift without the approval queue: the order goes straight to the vendor of the gift the requester picked. */
    DIRECT_VENDOR_ORDERING("Direct Vendor Ordering", "Requests skip approval and go straight to the vendor whose product was chosen, as an order.");

    private final String displayName;
    private final String description;

    CompanyFeature(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
