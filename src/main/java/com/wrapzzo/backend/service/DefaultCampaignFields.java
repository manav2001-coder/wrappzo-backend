package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.FieldType;
import java.util.List;

/**
 * The PRD 2.6 default submission field set -- seeded as real, editable CampaignField rows
 * on every new Campaign's form. The campaign creator can delete/reorder/edit any of these
 * or add arbitrary new fields afterward; this is only the starting point, not a fixed
 * schema.
 */
public final class DefaultCampaignFields {

    private DefaultCampaignFields() {
    }

    public record Def(String key, String label, FieldType type, boolean required) {
    }

    public static final List<Def> DEFAULTS = List.of(
            new Def("recipientName", "Recipient Name", FieldType.TEXT, true),
            new Def("recipientPhone", "Phone", FieldType.PHONE, true),
            new Def("recipientEmail", "Email", FieldType.EMAIL, false),
            new Def("recipientAddress", "Address", FieldType.TEXTAREA, false),
            new Def("recipientDob", "Date of Birth", FieldType.DATE, false),
            new Def("doctorName", "Doctor / HCP Name", FieldType.TEXT, false),
            new Def("hospital", "Hospital", FieldType.TEXT, false),
            new Def("territory", "Territory", FieldType.TEXT, false),
            new Def("photo", "Photo", FieldType.IMAGE, false),
            new Def("personalization", "Personalization", FieldType.TEXTAREA, false));
}
