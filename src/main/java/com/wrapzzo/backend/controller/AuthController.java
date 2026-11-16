package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.CurrentUserResponse;
import com.wrapzzo.backend.dto.ForgotPasswordRequest;
import com.wrapzzo.backend.dto.LoginRequest;
import com.wrapzzo.backend.dto.ResetPasswordRequest;
import org.springframework.http.ResponseEntity;
import com.wrapzzo.backend.service.PasswordResetDispatcher;
import com.wrapzzo.backend.service.PasswordResetService;
import com.wrapzzo.backend.dto.LogoutRequest;
import com.wrapzzo.backend.dto.RefreshRequest;
import com.wrapzzo.backend.dto.SetupPasswordRequest;
import com.wrapzzo.backend.dto.TokenResponse;
import com.wrapzzo.backend.security.PublicEndpoint;
import com.wrapzzo.backend.security.SignedInUser;
import com.wrapzzo.backend.service.AuthService;
import com.wrapzzo.backend.service.TokenPair;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetDispatcher passwordResetDispatcher;
    private final PasswordResetService passwordResetService;

    /** Always the same answer and the same speed, whether or not the account exists; the real work happens afterwards, off this thread. */
    private static final java.util.Map<String, String> ASKED = java.util.Map.of("message", "If that account exists, we have sent a link to reset the password");

    @PostMapping("/login")
    @PublicEndpoint
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    @PublicEndpoint
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @PublicEndpoint
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/setupPassword")
    @PublicEndpoint
    public TokenResponse setupPassword(@Valid @RequestBody SetupPasswordRequest request) {
        return toResponse(authService.setupPassword(request.token(), request.password(), request.confirmPassword()));
    }

    @PostMapping("/forgotPassword")
    @PublicEndpoint
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetDispatcher.forEmail(request.email());
        return ResponseEntity.accepted().body(ASKED);
    }

    /** Sets a new password from an emailed link. It does not sign the person in: they sign in themselves afterwards. */
    @PostMapping("/resetPassword")
    @PublicEndpoint
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SignedInUser
    public CurrentUserResponse me() {
        return authService.getCurrentUser();
    }

    private TokenResponse toResponse(TokenPair tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.accessTokenExpiresInSeconds());
    }
}
