package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.ArtworkApprovalScope;
import java.util.List;
import java.util.UUID;

public record ArtworkApprovalStepsResponse(List<Step> steps) {

    public record Step(int stepNo, UUID roleId, String roleName, ArtworkApprovalScope scope) {
    }
}
