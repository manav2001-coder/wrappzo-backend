package com.wrapzzo.backend.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Effective access-control keys for the calling user -- lets the frontend gate UI (nav
 * items, action buttons) without guessing from role names. companyId/companyName are null
 * for platform staff; a company user's own company, so self-service pages (e.g. Users) can
 * resolve "my company" without a companyId in the URL. managesTeam is true when anyone reports
 * to the caller (users.reports_to_id) -- what makes them an approver of their team's doctor
 * requests, which no access-control key expresses.
 */
public record CurrentUserResponse(Set<String> permissions, UUID companyId, String companyName, boolean managesTeam) {
}
