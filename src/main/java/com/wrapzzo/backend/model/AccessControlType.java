package com.wrapzzo.backend.model;

/**
 * FUNCTIONAL keys gate a real backend action and are enforced server-side.
 * UI keys only ever control frontend visibility (nav items, buttons) and
 * must never be relied on as the security boundary for an endpoint.
 */
public enum AccessControlType {
    FUNCTIONAL,
    UI
}
