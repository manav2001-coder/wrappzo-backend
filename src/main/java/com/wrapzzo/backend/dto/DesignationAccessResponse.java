package com.wrapzzo.backend.dto;

import java.util.Set;
import java.util.UUID;

public record DesignationAccessResponse(String designation, Set<UUID> accessControlIds) {
}
