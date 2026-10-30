package com.wrapzzo.backend.service;

import java.util.UUID;

/**
 * Something that decides who sees what in a company changed: a user's placement, extra units,
 * manager or company-wide flag, a user added or removed, an org unit created. Published inside
 * the changing transaction; VisibilityService drops the company's cached visibility once it commits.
 */
public record CompanyStructureChangedEvent(UUID companyId) {
}
