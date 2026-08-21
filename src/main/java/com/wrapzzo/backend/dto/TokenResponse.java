package com.wrapzzo.backend.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds) {
}
