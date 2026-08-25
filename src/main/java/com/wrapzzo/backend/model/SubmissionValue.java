package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One answer to one CampaignField. Exactly one of the value* columns is populated,
 * dictated by the field's FieldType: TEXT/TEXTAREA/EMAIL/PHONE/SELECT/RADIO -> valueText,
 * NUMBER -> valueNumber, BOOLEAN/CHECKBOX -> valueBoolean, DATE -> valueDate,
 * MULTISELECT -> valueList. IMAGE/FILE fields have no SubmissionValue at all -- the file
 * itself is the value, stored as a SubmissionAttachment.
 */
@Entity
@Table(name = "submission_values")
@Getter
@Setter
@NoArgsConstructor
public class SubmissionValue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_field_id", nullable = false)
    private CampaignField campaignField;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_number")
    private Double valueNumber;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "value_list", columnDefinition = "text[]")
    private List<String> valueList = new ArrayList<>();

    public SubmissionValue(Submission submission, CampaignField campaignField) {
        this.submission = submission;
        this.campaignField = campaignField;
    }
}
