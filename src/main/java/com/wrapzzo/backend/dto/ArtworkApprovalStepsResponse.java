package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.ArtworkApprovalScope;
import java.util.List;
import java.util.UUID;

/**
 * A company's artwork approval setup. enabled: whether Wrappzo has switched Senior Artwork Approval on for the company
 * (steps can only be changed while it is). levels: the simple "managers up the AM's line" count that applies while
 * there are no steps.
 */
public record ArtworkApprovalStepsResponse(boolean enabled, int levels, List<Step> steps, List<RoleOption> roles) {

    /** A role the company could name in a step -- offered here so setting steps needs no permission to browse roles. */
    public record RoleOption(UUID id, String name) {
    }

    public record Step(int stepNo, UUID roleId, String roleName, ArtworkApprovalScope scope) {
    }
}
