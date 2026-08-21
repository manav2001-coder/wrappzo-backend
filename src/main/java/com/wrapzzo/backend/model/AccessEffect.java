package com.wrapzzo.backend.model;

/**
 * A per-user override on top of their role's baseline access. REVOKE always
 * wins over whatever the role grants.
 */
public enum AccessEffect {
    GRANT,
    REVOKE
}
