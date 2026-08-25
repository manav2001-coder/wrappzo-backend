package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One field of a Campaign's submission form. New campaigns are seeded with the PRD's
 * default field set (see DefaultCampaignFields) -- the campaign creator can then delete,
 * reorder, edit, or add to these before activating.
 */
@Entity
@Table(name = "campaign_fields")
@Getter
@Setter
@NoArgsConstructor
public class CampaignField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_form_id", nullable = false)
    private CampaignForm campaignForm;

    /** Stable per-form key (e.g. "recipientName") -- what SubmissionValue rows key their answer to. */
    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 16)
    private FieldType fieldType;

    @Column(nullable = false)
    private boolean required = false;

    @Column
    private String placeholder;

    /** Selectable option labels -- only meaningful for SELECT/MULTISELECT/RADIO. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> options = new ArrayList<>();

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public CampaignField(
            CampaignForm campaignForm,
            String fieldKey,
            String label,
            FieldType fieldType,
            boolean required,
            String placeholder,
            List<String> options,
            int displayOrder) {
        this.campaignForm = campaignForm;
        this.fieldKey = fieldKey;
        this.label = label;
        this.fieldType = fieldType;
        this.required = required;
        this.placeholder = placeholder;
        this.options = options != null ? options : new ArrayList<>();
        this.displayOrder = displayOrder;
    }
}
