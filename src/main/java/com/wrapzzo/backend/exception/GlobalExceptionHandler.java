package com.wrapzzo.backend.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.LazyInitializationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Pattern ROW_INDEX = Pattern.compile("\\[(\\d+)]");

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
        List<String> details = e.getBindingResult().getAllErrors().stream().map(GlobalExceptionHandler::describe).toList();
        return validationFailed(details);
    }

    /** A body that is not JSON, or a value of the wrong type (a word where a number goes, an unknown enum value). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return validationFailed(List.of("the request body is malformed or has a value of the wrong type"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validationFailed(List.of("'" + e.getValue() + "' is not a valid " + readable(e.getName())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException e) {
        return validationFailed(List.of(readable(e.getParameterName()) + " is required"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        List<String> details = e.getConstraintViolations().stream()
                .map(violation -> readable(lastSegment(violation.getPropertyPath().toString())) + " " + violation.getMessage())
                .toList();
        return validationFailed(details);
    }

    /** The headline carries the reasons, so a client that shows only the message still says what is wrong. */
    private static ResponseEntity<Map<String, Object>> validationFailed(List<String> reasons) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Validation failed: " + String.join("; ", reasons) + ".", "details", reasons));
    }

    /** "name must not be blank", "row 3 contact email must be a well-formed email address". */
    private static String describe(ObjectError error) {
        String message = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
        return error instanceof FieldError field ? readable(field.getField()) + " " + message : message;
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    /** "rows[2].contactEmail" -> "row 3 contact email" (people count rows from 1); "name" -> "name". */
    static String readable(String path) {
        Matcher index = ROW_INDEX.matcher(path);
        StringBuilder words = new StringBuilder();
        while (index.find()) {
            index.appendReplacement(words, " row " + (Integer.parseInt(index.group(1)) + 1) + " ");
        }
        index.appendTail(words);
        return words.toString()
                .replaceFirst("^\\s*\\w+ (row \\d+)", "$1")
                .replace('.', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CompanyDeactivatedException.class)
    public ResponseEntity<Map<String, String>> handleCompanyDeactivated(CompanyDeactivatedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
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

    @ExceptionHandler(OrgUnitNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleOrgUnitNotFound(OrgUnitNotFoundException e) {
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

    @ExceptionHandler({RoleNotFoundException.class, TemplateNotFoundException.class, CompanyVendorNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleRoleOrTemplateNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidTemplateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTemplate(InvalidTemplateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidMenuLabelException.class)
    public ResponseEntity<Map<String, String>> handleInvalidMenuLabel(InvalidMenuLabelException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(QuantityBelowMinimumException.class)
    public ResponseEntity<Map<String, String>> handleQuantityBelowMinimum(QuantityBelowMinimumException e) {
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

    @ExceptionHandler(PhotoOrEmailRequiredException.class)
    public ResponseEntity<Map<String, String>> handlePhotoOrEmailRequired(PhotoOrEmailRequiredException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
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

    @ExceptionHandler(SubmissionValidationException.class)
    public ResponseEntity<Map<String, Object>> handleSubmissionValidation(SubmissionValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Submission validation failed.", "details", e.getErrors()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleOrderNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(WarehouseNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleWarehouseNotFound(WarehouseNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidOrderStatusTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidOrderStatusTransition(InvalidOrderStatusTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ArtworkNotApprovedException.class)
    public ResponseEntity<Map<String, String>> handleArtworkNotApproved(ArtworkNotApprovedException e) {
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

    @ExceptionHandler(OrderDesignNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleOrderDesignNotFound(OrderDesignNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(OrderDesignAlreadyReviewedException.class)
    public ResponseEntity<Map<String, String>> handleOrderDesignAlreadyReviewed(OrderDesignAlreadyReviewedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RecipientAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleRecipientAlreadyExists(RecipientAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(PcodeRequiredException.class)
    public ResponseEntity<Map<String, String>> handlePcodeRequired(PcodeRequiredException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidArtworkApprovalStepsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidArtworkApprovalSteps(InvalidArtworkApprovalStepsException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidVendorCoverageException.class)
    public ResponseEntity<Map<String, String>> handleInvalidVendorCoverage(InvalidVendorCoverageException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidArtworkFileException.class)
    public ResponseEntity<Map<String, String>> handleInvalidArtworkFile(InvalidArtworkFileException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
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
        // Logged: this is a bug (a lazy field read outside a transaction), and without a trace it is invisible.
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Lazy association read outside a transaction", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "The server could not load part of this data. This is a fault on our side, not something you did."));
    }
}
