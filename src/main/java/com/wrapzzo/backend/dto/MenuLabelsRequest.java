package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Replace-all: every menu the company has renamed, menu id -> its name. A menu left out goes back to its default. */
public record MenuLabelsRequest(@NotNull @Size(max = 100) Map<String, String> labels) {
}
