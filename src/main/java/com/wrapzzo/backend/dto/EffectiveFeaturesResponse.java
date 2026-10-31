package com.wrapzzo.backend.dto;

import java.util.List;
import java.util.UUID;

/** What a template grants: its own features, and the ones it gets from the templates it includes. */
public record EffectiveFeaturesResponse(List<Entry> direct, List<Entry> inherited) {

    public record Entry(UUID id, String key, String moduleName, String actionName) {
    }
}
