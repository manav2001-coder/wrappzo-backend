package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotNull;

/** true: the user sees the whole company; false: what their place in the org tree gives them. */
public record SetCompanyWideRequest(@NotNull Boolean companyWide) {
}
