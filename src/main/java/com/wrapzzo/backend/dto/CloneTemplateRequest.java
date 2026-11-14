package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Size;

/** The name for the copy; left out, it is "Copy of <the original>". */
public record CloneTemplateRequest(@Size(max = 100) String name) {
}
