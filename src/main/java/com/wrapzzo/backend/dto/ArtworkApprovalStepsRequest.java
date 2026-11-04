package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.ArtworkApprovalScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The ordered steps after the AM's own review; an empty list goes back to "N managers up the AM's line". */
public record ArtworkApprovalStepsRequest(@NotNull @Size(max = 5) List<@Valid Step> steps) {

    public record Step(@NotNull UUID roleId, @NotNull ArtworkApprovalScope scope) {
    }
}
