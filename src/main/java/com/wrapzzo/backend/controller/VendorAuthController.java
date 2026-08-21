package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.LoginRequest;
import com.wrapzzo.backend.dto.LogoutRequest;
import com.wrapzzo.backend.dto.RefreshRequest;
import com.wrapzzo.backend.dto.TokenResponse;
import com.wrapzzo.backend.service.TokenPair;
import com.wrapzzo.backend.service.VendorAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendor/auth")
@RequiredArgsConstructor
public class VendorAuthController {

    private final VendorAuthService vendorAuthService;

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(vendorAuthService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toResponse(vendorAuthService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        vendorAuthService.logout(request.refreshToken());
    }

    private TokenResponse toResponse(TokenPair tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.accessTokenExpiresInSeconds());
    }
}
