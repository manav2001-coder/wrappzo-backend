package com.wrapzzo.backend.dto;

import java.util.List;

/** Each candidate is judged alone, so one that would loop doesn't sink the rest; {@code errors} say why. */
public record AddReferencesResponse(int added, int totalRequested, List<String> errors) {
}
