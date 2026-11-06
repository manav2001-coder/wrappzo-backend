package com.wrapzzo.backend.dto;

/** A stored photo: where to see it, and the name it downloads as. */
public record PhotoResponse(String url, String fileName) {
}
