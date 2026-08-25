package com.wrapzzo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Replace-all semantics -- the whole audience list is resubmitted and replaces the campaign's current audience. */
public record UpdateCampaignAudienceRequest(@NotEmpty List<@Valid CampaignAudienceRequest> audiences) {
}
