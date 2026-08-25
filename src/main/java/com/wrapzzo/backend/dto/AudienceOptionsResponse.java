package com.wrapzzo.backend.dto;

import java.util.List;
import java.util.UUID;

/** Lightweight company-scoped picklists for building a Campaign's audience -- org units and users. */
public record AudienceOptionsResponse(List<OrgUnitOption> orgUnits, List<UserOption> users) {

    public record OrgUnitOption(UUID id, String name, String levelName) {
    }

    public record UserOption(UUID id, String name, String email) {
    }
}
