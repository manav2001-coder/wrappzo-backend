package com.wrapzzo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Replace-all semantics -- the whole field list is resubmitted and replaces the form's current fields. */
public record UpdateCampaignFieldsRequest(@NotEmpty List<@Valid CampaignFieldRequest> fields) {
}
