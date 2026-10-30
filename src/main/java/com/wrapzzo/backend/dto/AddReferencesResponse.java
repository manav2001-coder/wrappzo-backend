package com.wrapzzo.backend.dto;

import java.util.List;
import java.util.UUID;

/** What came of adding includes: each candidate is judged alone, so one bad one doesn't sink the rest. */
public record AddReferencesResponse(int added, int totalRequested, List<Rejection> errors, TemplateResponse template) {

    public record Rejection(UUID templateId, String name, String reason) {
    }
}
