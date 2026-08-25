package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.CampaignField;
import com.wrapzzo.backend.model.FieldType;
import java.util.List;
import java.util.UUID;

public record CampaignFieldResponse(
        UUID id,
        String fieldKey,
        String label,
        FieldType fieldType,
        boolean required,
        String placeholder,
        List<String> options,
        int displayOrder) {

    public static CampaignFieldResponse from(CampaignField field) {
        return new CampaignFieldResponse(
                field.getId(),
                field.getFieldKey(),
                field.getLabel(),
                field.getFieldType(),
                field.isRequired(),
                field.getPlaceholder(),
                field.getOptions(),
                field.getDisplayOrder());
    }
}
