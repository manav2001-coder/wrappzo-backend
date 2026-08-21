package com.wrapzzo.backend.service;

public record TokenPair(String accessToken, String refreshToken, long accessTokenExpiresInSeconds) {
}
