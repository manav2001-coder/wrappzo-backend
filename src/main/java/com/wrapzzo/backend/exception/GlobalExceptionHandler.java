package com.wrapzzo.backend.exception;

import java.util.List;
import java.util.Map;
import org.hibernate.LazyInitializationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Safety net for plain @Valid/@NotBlank/@Email-style request-body validation failures
     * that reach no domain-specific handler above -- same "opaque 403 with no body" problem
     * as DataIntegrityViolationException and LazyInitializationException below: with no
     * handler, this bubbles up uncaught and Spring's error-dispatch machinery turns it into
     * a bodyless 403 instead of a real 400. That reads as a permissions error and is
     * indistinguishable from an actual auth failure client-side, e.g. a bulk import row
     * with a malformed email surfaced as "Request failed with status code 403" instead of
     * a row-level message. Real 400 with the actual field errors here instead.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Validation failed.", "details", details));
    }

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

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(LastCompanyAdminException.class)
    public ResponseEntity<Map<String, String>> handleLastCompanyAdmin(LastCompanyAdminException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UserHasActivityException.class)
    public ResponseEntity<Map<String, String>> handleUserHasActivity(UserHasActivityException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CannotDeleteSelfException.class)
    public ResponseEntity<Map<String, String>> handleCannotDeleteSelf(CannotDeleteSelfException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidAccessControlSelectionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidAccessControlSelection(InvalidAccessControlSelectionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
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

    @ExceptionHandler(InvalidRecipientUploadTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRecipientUploadToken(InvalidRecipientUploadTokenException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SubmissionNotApprovedException.class)
    public ResponseEntity<Map<String, String>> handleSubmissionNotApproved(SubmissionNotApprovedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RecipientPhotoRequestNotPossibleException.class)
    public ResponseEntity<Map<String, String>> handleRecipientPhotoRequestNotPossible(RecipientPhotoRequestNotPossibleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
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

    @ExceptionHandler(CampaignNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCampaignNotFound(CampaignNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CampaignClosedException.class)
    public ResponseEntity<Map<String, String>> handleCampaignClosed(CampaignClosedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CampaignNotActiveException.class)
    public ResponseEntity<Map<String, String>> handleCampaignNotActive(CampaignNotActiveException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NotInCampaignAudienceException.class)
    public ResponseEntity<Map<String, String>> handleNotInCampaignAudience(NotInCampaignAudienceException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SubmissionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSubmissionNotFound(SubmissionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SubmissionAlreadyReviewedException.class)
    public ResponseEntity<Map<String, String>> handleSubmissionAlreadyReviewed(SubmissionAlreadyReviewedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NotAuthorizedToReviewException.class)
    public ResponseEntity<Map<String, String>> handleNotAuthorizedToReview(NotAuthorizedToReviewException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SubmissionValidationException.class)
    public ResponseEntity<Map<String, Object>> handleSubmissionValidation(SubmissionValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Submission validation failed.", "details", e.getErrors()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleOrderNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidOrderStatusTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidOrderStatusTransition(InvalidOrderStatusTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CampaignAudienceRequiredException.class)
    public ResponseEntity<Map<String, String>> handleCampaignAudienceRequired(CampaignAudienceRequiredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CampaignFieldsLockedException.class)
    public ResponseEntity<Map<String, String>> handleCampaignFieldsLocked(CampaignFieldsLockedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(GiftNotOfferedByCampaignException.class)
    public ResponseEntity<Map<String, String>> handleGiftNotOfferedByCampaign(GiftNotOfferedByCampaignException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NoAvailableVendorException.class)
    public ResponseEntity<Map<String, String>> handleNoAvailableVendor(NoAvailableVendorException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(GiftNotAvailableForQuickGiftException.class)
    public ResponseEntity<Map<String, String>> handleGiftNotAvailableForQuickGift(GiftNotAvailableForQuickGiftException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(QuickGiftRequestNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleQuickGiftRequestNotFound(QuickGiftRequestNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(QuickGiftAlreadyReviewedException.class)
    public ResponseEntity<Map<String, String>> handleQuickGiftAlreadyReviewed(QuickGiftAlreadyReviewedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(QuickGiftNotApprovedException.class)
    public ResponseEntity<Map<String, String>> handleQuickGiftNotApproved(QuickGiftNotApprovedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RecipientAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleRecipientAlreadyExists(RecipientAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RecipientNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleRecipientNotFound(RecipientNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
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
