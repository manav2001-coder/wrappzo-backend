package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.Size;

/** Approving a proof in place of the levels below; the reason is optional. */
public record OverrideOrderDesignRequest(@Size(max = 1000) String comment) {
}
