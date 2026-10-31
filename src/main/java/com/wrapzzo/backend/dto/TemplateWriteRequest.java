package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Size;

/** Create (name required) or patch (anything left out is unchanged) a template. */
public record TemplateWriteRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 500) String description,
        @Size(max = 50) String icon,
        @Size(max = 255) String path,
        Boolean active) {
}
