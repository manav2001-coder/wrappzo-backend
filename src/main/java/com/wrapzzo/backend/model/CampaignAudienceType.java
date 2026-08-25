package com.wrapzzo.backend.model;

/**
 * Who may submit into a Campaign, resolved from this codebase's existing hierarchy
 * primitives rather than a new org-chart concept. ORG_UNIT uses OrgUnit.ancestorIds
 * (everyone under that node); MANAGER_TEAM uses User.reportsTo (everyone found by
 * walking the anchor manager's reports, recursively); SELECTED_USERS is one row per
 * individually-included user. ALL_COMPANY_USERS has no anchor.
 */
public enum CampaignAudienceType {
    ALL_COMPANY_USERS,
    ORG_UNIT,
    MANAGER_TEAM,
    SELECTED_USERS
}
