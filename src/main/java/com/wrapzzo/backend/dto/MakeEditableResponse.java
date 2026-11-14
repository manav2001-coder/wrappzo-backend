package com.wrapzzo.backend.dto;

/** How many inherited features became the template's own, and how many included templates were let go. */
public record MakeEditableResponse(int copied, int includesRemoved) {
}
