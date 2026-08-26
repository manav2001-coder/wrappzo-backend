package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.PresignSubmissionAttachmentResponse;
import com.wrapzzo.backend.dto.RecipientUploadDetailsResponse;
import com.wrapzzo.backend.dto.SubmitRecipientPhotoRequest;
import com.wrapzzo.backend.exception.InvalidRecipientUploadTokenException;
import com.wrapzzo.backend.model.CampaignField;
import com.wrapzzo.backend.model.CampaignForm;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.RecipientUploadToken;
import com.wrapzzo.backend.model.Submission;
import com.wrapzzo.backend.model.SubmissionAttachment;
import com.wrapzzo.backend.model.SubmissionValue;
import com.wrapzzo.backend.repository.CampaignFieldRepository;
import com.wrapzzo.backend.repository.CampaignFormRepository;
import com.wrapzzo.backend.repository.GiftImageRepository;
import com.wrapzzo.backend.repository.RecipientUploadTokenRepository;
import com.wrapzzo.backend.repository.SubmissionAttachmentRepository;
import com.wrapzzo.backend.repository.SubmissionValueRepository;
import com.wrapzzo.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Get the recipient's own photo" side-flow: if a Member submits a recipient without one
 * (the default Photo field is optional, PRD's field set), once a Company Admin approves
 * that submission we hand the recipient a one-time link (emailed, since we already have a
 * real sender wired up) to upload it themselves -- no login required. Best-effort: if the
 * recipient's email wasn't collected either, this silently does nothing rather than
 * blocking the approval that already succeeded.
 */
@Service
public class RecipientUploadService {

    /** The default campaign field set's Photo field key (see DefaultCampaignFields). */
    private static final String PHOTO_FIELD_KEY = "photo";
    private static final Duration UPLOAD_TOKEN_EXPIRY = Duration.ofDays(7);
    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final RecipientUploadTokenRepository tokenRepository;
    private final SubmissionValueRepository submissionValueRepository;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;
    private final CampaignFormRepository campaignFormRepository;
    private final CampaignFieldRepository campaignFieldRepository;
    private final GiftImageRepository giftImageRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendBaseUrl;

    public RecipientUploadService(
            RecipientUploadTokenRepository tokenRepository,
            SubmissionValueRepository submissionValueRepository,
            SubmissionAttachmentRepository submissionAttachmentRepository,
            CampaignFormRepository campaignFormRepository,
            CampaignFieldRepository campaignFieldRepository,
            GiftImageRepository giftImageRepository,
            FileStorageService fileStorageService,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.submissionValueRepository = submissionValueRepository;
        this.submissionAttachmentRepository = submissionAttachmentRepository;
        this.campaignFormRepository = campaignFormRepository;
        this.campaignFieldRepository = campaignFieldRepository;
        this.giftImageRepository = giftImageRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Called from SubmissionService.approve() right after the Order is created. Returns
     * the freshly-issued upload link (already emailed) so the Admin UI can also offer a
     * one-click "share via WhatsApp" for it, or null if nothing needed to be requested.
     */
    @Transactional
    public String requestPhotoIfMissing(Submission submission) {
        Optional<CampaignForm> form = campaignFormRepository.findByCampaignId(submission.getCampaign().getId());
        if (form.isEmpty()) {
            return null;
        }
        Optional<CampaignField> photoField = campaignFieldRepository.findByCampaignFormIdAndFieldKey(form.get().getId(), PHOTO_FIELD_KEY);
        if (photoField.isEmpty() || hasPhoto(submission.getId())) {
            return null;
        }
        String recipientEmail = valueText(submission.getId(), "recipientEmail");
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return null;
        }

        String rawToken = TokenHasher.generateOpaqueToken();
        Instant expiresAt = Instant.now().plus(UPLOAD_TOKEN_EXPIRY);
        tokenRepository.save(new RecipientUploadToken(submission, TokenHasher.sha256Hex(rawToken), expiresAt));

        String recipientName = valueText(submission.getId(), "recipientName");
        Gift gift = submission.getGift();
        String uploadUrl = frontendBaseUrl + "/recipient-upload/" + rawToken;
        eventPublisher.publishEvent(new RecipientPhotoRequestedEvent(new PendingRecipientPhotoRequest(
                recipientEmail,
                recipientName != null ? recipientName : "there",
                submission.getCompany().getName(),
                gift != null ? gift.getName() : "your gift",
                uploadUrl,
                UPLOAD_TOKEN_EXPIRY.toDays() * 24)));
        return uploadUrl;
    }

    @Transactional(readOnly = true)
    public RecipientUploadDetailsResponse getDetails(String rawToken) {
        RecipientUploadToken token = tokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(InvalidRecipientUploadTokenException::new);
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRecipientUploadTokenException();
        }

        Submission submission = token.getSubmission();
        Gift gift = submission.getGift();
        List<String> imageUrls = gift == null
                ? List.of()
                : giftImageRepository.findByGiftIdOrderByDisplayOrderAsc(gift.getId()).stream()
                        .map(image -> fileStorageService.publicUrl(image.getImageKey()))
                        .toList();
        boolean alreadyUploaded = token.getUsedAt() != null || hasPhoto(submission.getId());

        return new RecipientUploadDetailsResponse(
                valueText(submission.getId(), "recipientName"),
                submission.getCompany().getName(),
                gift != null ? gift.getName() : null,
                gift != null ? gift.getDescription() : null,
                imageUrls,
                alreadyUploaded);
    }

    @Transactional
    public PresignSubmissionAttachmentResponse presignUpload(String rawToken, String contentType) {
        RecipientUploadToken token = resolveActiveToken(rawToken);
        String key = "recipient-uploads/%s/%s".formatted(token.getSubmission().getId(), UUID.randomUUID());
        String uploadUrl = fileStorageService.presignUpload(key, contentType, UPLOAD_URL_EXPIRY);
        return new PresignSubmissionAttachmentResponse(uploadUrl, key);
    }

    @Transactional
    public void submitPhoto(String rawToken, SubmitRecipientPhotoRequest request) {
        RecipientUploadToken token = resolveActiveToken(rawToken);
        Submission submission = token.getSubmission();
        CampaignForm form = campaignFormRepository
                .findByCampaignId(submission.getCampaign().getId())
                .orElseThrow(InvalidRecipientUploadTokenException::new);
        CampaignField photoField = campaignFieldRepository
                .findByCampaignFormIdAndFieldKey(form.getId(), PHOTO_FIELD_KEY)
                .orElseThrow(InvalidRecipientUploadTokenException::new);

        submissionAttachmentRepository.save(new SubmissionAttachment(
                submission, photoField, request.key(), request.fileName(), request.mimeType(), request.fileSize()));
        token.setUsedAt(Instant.now());
    }

    private boolean hasPhoto(UUID submissionId) {
        return submissionAttachmentRepository.findBySubmissionId(submissionId).stream()
                .anyMatch(a -> PHOTO_FIELD_KEY.equals(a.getCampaignField().getFieldKey()));
    }

    private String valueText(UUID submissionId, String fieldKey) {
        return submissionValueRepository
                .findBySubmissionIdAndCampaignFieldFieldKey(submissionId, fieldKey)
                .map(SubmissionValue::getValueText)
                .orElse(null);
    }

    private RecipientUploadToken resolveActiveToken(String rawToken) {
        RecipientUploadToken token = tokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(InvalidRecipientUploadTokenException::new);
        if (!token.isActive()) {
            throw new InvalidRecipientUploadTokenException();
        }
        return token;
    }
}
