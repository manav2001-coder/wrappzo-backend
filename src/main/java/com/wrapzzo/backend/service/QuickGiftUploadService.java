package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.PresignSubmissionAttachmentResponse;
import com.wrapzzo.backend.dto.RecipientUploadDetailsResponse;
import com.wrapzzo.backend.dto.SubmitRecipientPhotoRequest;
import com.wrapzzo.backend.exception.InvalidRecipientUploadTokenException;
import com.wrapzzo.backend.exception.RecipientPhotoRequestNotPossibleException;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftUploadToken;
import com.wrapzzo.backend.repository.GiftImageRepository;
import com.wrapzzo.backend.repository.QuickGiftUploadTokenRepository;
import com.wrapzzo.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Get the recipient's own photo" side-flow for Quick Gift -- same idea as
 * RecipientUploadService for Campaign submissions, simplified since a Quick Gift's photo is
 * a direct column, not an EAV field lookup. Runs once a Company Admin approves the request
 * and no photo was attached at request time.
 */
@Service
public class QuickGiftUploadService {

    private static final Duration UPLOAD_TOKEN_EXPIRY = Duration.ofDays(7);
    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final QuickGiftUploadTokenRepository tokenRepository;
    private final GiftImageRepository giftImageRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendBaseUrl;

    public QuickGiftUploadService(
            QuickGiftUploadTokenRepository tokenRepository,
            GiftImageRepository giftImageRepository,
            FileStorageService fileStorageService,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.giftImageRepository = giftImageRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Called from QuickGiftService.approve() right after the Order is created. Returns the
     * freshly-issued upload link (already emailed) so the Admin UI can also offer a one-click
     * "share via WhatsApp" for it, or null if nothing needed to be requested. Best-effort:
     * silently does nothing without a recipient email, since this must never block the approval.
     */
    @Transactional
    public String requestPhotoIfMissing(QuickGiftRequest request) {
        if (request.hasPhoto()) {
            return null;
        }
        String recipientEmail = request.getRecipientEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return null;
        }
        return issueTokenAndSend(request, recipientEmail);
    }

    /** Explicitly triggered by the requester -- e.g. the recipient's first photo wasn't clear enough. */
    @Transactional
    public String requestAgain(QuickGiftRequest request) {
        String recipientEmail = request.getRecipientEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new RecipientPhotoRequestNotPossibleException("This recipient has no email on file to send the link to.");
        }
        return issueTokenAndSend(request, recipientEmail);
    }

    private String issueTokenAndSend(QuickGiftRequest request, String recipientEmail) {
        // Retire any still-active older links for this request first -- a resend ("photo wasn't
        // clear, send it again") should mean exactly one working link, not two.
        for (QuickGiftUploadToken old : tokenRepository.findByQuickGiftRequestId(request.getId())) {
            if (old.isActive()) {
                old.setUsedAt(Instant.now());
            }
        }

        String rawToken = TokenHasher.generateOpaqueToken();
        Instant expiresAt = Instant.now().plus(UPLOAD_TOKEN_EXPIRY);
        tokenRepository.save(new QuickGiftUploadToken(request, TokenHasher.sha256Hex(rawToken), expiresAt));

        Gift gift = request.getGift();
        String uploadUrl = frontendBaseUrl + "/quick-gift-upload/" + rawToken;
        eventPublisher.publishEvent(new RecipientPhotoRequestedEvent(new PendingRecipientPhotoRequest(
                recipientEmail,
                request.getRecipientName() != null ? request.getRecipientName() : "there",
                request.getCompany().getName(),
                gift != null ? gift.getName() : "your gift",
                uploadUrl,
                UPLOAD_TOKEN_EXPIRY.toDays() * 24)));
        return uploadUrl;
    }

    @Transactional(readOnly = true)
    public RecipientUploadDetailsResponse getDetails(String rawToken) {
        QuickGiftUploadToken token = tokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(InvalidRecipientUploadTokenException::new);
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRecipientUploadTokenException();
        }

        QuickGiftRequest request = token.getQuickGiftRequest();
        Gift gift = request.getGift();
        List<String> imageUrls = gift == null
                ? List.of()
                : giftImageRepository.findByGiftIdOrderByDisplayOrderAsc(gift.getId()).stream()
                        .map(image -> fileStorageService.publicUrl(image.getImageKey()))
                        .toList();
        // Deliberately just this token's own usedAt -- NOT request.hasPhoto(), which is true
        // precisely when this is a resend (the old photo wasn't right, a fresh unused token was
        // just issued) and would otherwise show "already uploaded" and block the re-upload.
        boolean alreadyUploaded = token.getUsedAt() != null;

        return new RecipientUploadDetailsResponse(
                request.getRecipientName(),
                request.getCompany().getName(),
                gift != null ? gift.getName() : null,
                gift != null ? gift.getDescription() : null,
                imageUrls,
                alreadyUploaded);
    }

    @Transactional
    public PresignSubmissionAttachmentResponse presignUpload(String rawToken, String contentType) {
        QuickGiftUploadToken token = resolveActiveToken(rawToken);
        String key = "quick-gift-uploads/%s/%s".formatted(token.getQuickGiftRequest().getId(), UUID.randomUUID());
        String uploadUrl = fileStorageService.presignUpload(key, contentType, UPLOAD_URL_EXPIRY);
        return new PresignSubmissionAttachmentResponse(uploadUrl, key);
    }

    @Transactional
    public void submitPhoto(String rawToken, SubmitRecipientPhotoRequest request) {
        QuickGiftUploadToken token = resolveActiveToken(rawToken);
        QuickGiftRequest quickGift = token.getQuickGiftRequest();

        // A re-request (requestAgain) can land here a second time -- replace, don't leak the old file.
        if (quickGift.getPhotoKey() != null) {
            fileStorageService.delete(quickGift.getPhotoKey());
        }
        quickGift.setPhotoKey(request.key());
        quickGift.setPhotoFileName(request.fileName());
        quickGift.setPhotoContentType(request.mimeType());
        token.setUsedAt(Instant.now());
    }

    private QuickGiftUploadToken resolveActiveToken(String rawToken) {
        QuickGiftUploadToken token = tokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(InvalidRecipientUploadTokenException::new);
        if (!token.isActive()) {
            throw new InvalidRecipientUploadTokenException();
        }
        return token;
    }
}
