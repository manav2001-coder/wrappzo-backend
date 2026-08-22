package com.wrapzzo.backend.exception;

import java.util.Map;
import org.hibernate.LazyInitializationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Map<String, String>> handleAccountLocked(AccountLockedException e) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(Map.of("error", e.getMessage(), "lockedUntil", e.getLockedUntil().toString()));
    }

    @ExceptionHandler(CompanyNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCompanyNotFound(CompanyNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(GiftNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleGiftNotFound(GiftNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(GiftImageNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleGiftImageNotFound(GiftImageNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(VendorNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleVendorNotFound(VendorNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<Map<String, String>> handlePasswordMismatch(PasswordMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(VendorEmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleVendorEmailAlreadyExists(VendorEmailAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidInviteTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInviteToken(InvalidInviteTokenException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<Map<String, String>> handleEmailDeliveryFailure(EmailDeliveryException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Could not send the invite email right now -- please try again."));
    }

    @ExceptionHandler(GiftNameAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleGiftNameAlreadyExists(GiftNameAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(VendorGiftEditNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleVendorGiftEditNotAllowed(VendorGiftEditNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UserImportValidationException.class)
    public ResponseEntity<Map<String, Object>> handleUserImportValidation(UserImportValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Import validation failed -- fix these rows and re-upload.", "details", e.getErrors()));
    }

    /**
     * Safety net for any unique/foreign-key violation that reaches the database without an
     * explicit pre-check above -- without this, it bubbles up uncaught and Spring's
     * error-dispatch machinery turns it into an opaque 403 with no body instead of a real error.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "This conflicts with existing data -- a unique value may already be in use."));
    }

    /**
     * Safety net for a lazy association touched outside an open Hibernate session -- e.g. a
     * @Transactional-less read method whose response-mapping code accesses a lazy field. Same
     * "opaque 403 with no body" problem as DataIntegrityViolationException above: surface it as
     * a real 500 with a message instead, so the underlying missing @Transactional is obvious.
     */
    @ExceptionHandler(LazyInitializationException.class)
    public ResponseEntity<Map<String, String>> handleLazyInitialization(LazyInitializationException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Something went wrong loading this data. Please try again."));
    }
}
