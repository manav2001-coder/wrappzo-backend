package com.wrapzzo.backend.service;

import java.util.List;
import java.util.UUID;

/** Someone approved a proof in place of the people below them; those people are told after commit. */
public record ArtworkBypassedEvent(UUID designId, UUID bypassedById, List<UUID> skippedUserIds, String comment) {
}
