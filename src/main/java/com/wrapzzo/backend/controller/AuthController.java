package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.LoginRequest;
import com.wrapzzo.backend.dto.LogoutRequest;
import com.wrapzzo.backend.dto.RefreshRequest;
import com.wrapzzo.backend.dto.TokenResponse;
import com.wrapzzo.backend.service.AuthService;
import com.wrapzzo.backend.service.TokenPair;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    private TokenResponse toResponse(TokenPair tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.accessTokenExpiresInSeconds());
    }
}
