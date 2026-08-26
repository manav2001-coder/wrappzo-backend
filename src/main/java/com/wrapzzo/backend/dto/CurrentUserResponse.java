package com.wrapzzo.backend.dto;

import java.util.Set;

/** Effective access-control keys for the calling user -- lets the frontend gate UI (nav items, action buttons) without guessing from role names. */
public record CurrentUserResponse(Set<String> permissions) {
}
