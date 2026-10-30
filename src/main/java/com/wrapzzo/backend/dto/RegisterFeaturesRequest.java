package com.wrapzzo.backend.dto;

import java.util.Set;
import java.util.UUID;

/** Features to register for a company; left out (null), every company-assignable feature it is missing. */
public record RegisterFeaturesRequest(Set<UUID> featureIds) {
}
