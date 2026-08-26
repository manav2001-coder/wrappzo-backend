package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.CreateSubmissionRequest;
import com.wrapzzo.backend.dto.PresignSubmissionAttachmentResponse;
import com.wrapzzo.backend.dto.SubmissionAttachmentDto;
import com.wrapzzo.backend.dto.SubmissionResponse;
import com.wrapzzo.backend.dto.SubmissionValueDto;
import com.wrapzzo.backend.exception.CampaignClosedException;
import com.wrapzzo.backend.exception.CampaignNotActiveException;
import com.wrapzzo.backend.exception.GiftNotFoundException;
import com.wrapzzo.backend.exception.GiftNotOfferedByCampaignException;
import com.wrapzzo.backend.exception.NotAuthorizedToReviewException;
import com.wrapzzo.backend.exception.NotInCampaignAudienceException;
import com.wrapzzo.backend.exception.SubmissionAlreadyReviewedException;
import com.wrapzzo.backend.exception.SubmissionNotApprovedException;
import com.wrapzzo.backend.exception.SubmissionNotFoundException;
import com.wrapzzo.backend.exception.SubmissionValidationException;
import com.wrapzzo.backend.model.Campaign;
import com.wrapzzo.backend.model.CampaignField;
import com.wrapzzo.backend.model.CampaignForm;
import com.wrapzzo.backend.model.CampaignStatus;
import com.wrapzzo.backend.model.FieldType;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.Submission;
import com.wrapzzo.backend.model.SubmissionAttachment;
import com.wrapzzo.backend.model.SubmissionStatus;
import com.wrapzzo.backend.model.SubmissionValue;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CampaignFieldRepository;
import com.wrapzzo.backend.repository.CampaignFormRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.SubmissionAttachmentRepository;
import com.wrapzzo.backend.repository.SubmissionRepository;
import com.wrapzzo.backend.repository.SubmissionValueRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private static final Duration ATTACHMENT_UPLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final SubmissionRepository submissionRepository;
    private final SubmissionValueRepository submissionValueRepository;
    private final GiftRepository giftRepository;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;
    private final CampaignFormRepository campaignFormRepository;
    private final CampaignFieldRepository campaignFieldRepository;
    private final OrderRepository orderRepository;
    private final CampaignService campaignService;
    private final FileStorageService fileStorageService;
    private final RecipientUploadService recipientUploadService;

    public PresignSubmissionAttachmentResponse presignAttachment(UUID campaignId, String contentType) {
        Campaign campaign = campaignService.getEntity(campaignId);
        requireActive(campaign);
        requireInAudience(campaign);
        String key = "submissions/%s/%s/%s".formatted(campaignId, campaignService.currentUser().getId(), UUID.randomUUID());
        String uploadUrl = fileStorageService.presignUpload(key, contentType, ATTACHMENT_UPLOAD_URL_EXPIRY);
        return new PresignSubmissionAttachmentResponse(uploadUrl, key);
    }

    @Transactional
    public SubmissionResponse create(UUID campaignId, CreateSubmissionRequest request) {
        Campaign campaign = campaignService.getEntity(campaignId);
        requireActive(campaign);
        requireInAudience(campaign);
        Gift gift = resolveOfferedGift(campaign, request.giftId());

        User submitter = campaignService.currentUser();
        Submission submission = submissionRepository.save(new Submission(campaign.getCompany(), campaign, submitter, gift));
        applyValues(submission, campaignId, request.values());
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(Instant.now());
        return toResponse(submission);
    }

    @Transactional
    public SubmissionResponse resubmit(UUID submissionId, CreateSubmissionRequest request) {
        Submission submission = getOwnEntity(submissionId);
        if (submission.getStatus() != SubmissionStatus.REJECTED) {
            throw new SubmissionAlreadyReviewedException(submissionId);
        }
        Campaign campaign = submission.getCampaign();
        requireActive(campaign);
        submission.setGift(resolveOfferedGift(campaign, request.giftId()));

        submissionAttachmentRepository.findBySubmissionId(submissionId).forEach(a -> {
            fileStorageService.delete(a.getFileKey());
            submissionAttachmentRepository.delete(a);
        });
        submissionValueRepository.findBySubmissionId(submissionId).forEach(submissionValueRepository::delete);

        applyValues(submission, campaign.getId(), request.values());
        submission.setStatus(SubmissionStatus.RESUBMITTED);
        submission.setSubmittedAt(Instant.now());
        submission.setRejectionReason(null);
        return toResponse(submission);
    }

    /** Gift choice is mandatory and must be one the campaign actually offers -- not assumed, not free-form. */
    private Gift resolveOfferedGift(Campaign campaign, UUID giftId) {
        Gift gift = giftRepository.findById(giftId).orElseThrow(() -> new GiftNotFoundException(giftId));
        if (campaign.getGifts().stream().noneMatch(g -> g.getId().equals(giftId))) {
            throw new GiftNotOfferedByCampaignException(giftId);
        }
        return gift;
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> listForCampaign(UUID campaignId) {
        campaignService.getEntity(campaignId);
        return submissionRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> listMine() {
        User user = campaignService.currentUser();
        return submissionRepository.findBySubmittedByIdOrderByCreatedAtDesc(user.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> listPendingApproval() {
        User approver = campaignService.currentUser();
        List<SubmissionStatus> pending = List.of(SubmissionStatus.SUBMITTED, SubmissionStatus.RESUBMITTED);
        return submissionRepository.findByCompanyIdAndStatusInOrderByCreatedAtDesc(approver.getCompany().getId(), pending).stream()
                .filter(s -> canReview(s, approver))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SubmissionResponse approve(UUID submissionId) {
        Submission submission = getEntity(submissionId);
        User approver = campaignService.currentUser();
        requireReviewable(submission, approver);

        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedBy(approver);
        submission.setReviewedAt(Instant.now());

        // Idempotency guard: orders.submission_id is also DB-unique (PRD 4.6) -- this check
        // just turns a double-click into a clean 409 instead of a raw constraint violation.
        if (orderRepository.findBySubmissionId(submissionId).isPresent()) {
            throw new SubmissionAlreadyReviewedException(submissionId);
        }
        String recipientName = valueText(submissionId, "recipientName");
        String recipientPhone = valueText(submissionId, "recipientPhone");
        Order order = new Order(
                submission.getCompany(), submission.getCampaign(), submission, submission.getGift(),
                recipientName, recipientPhone, approver);
        orderRepository.save(order);

        // Best-effort: if this recipient wasn't given a photo at submission time (the
        // default Photo field is optional), hand them a self-serve upload link now that
        // there's a real Order to fulfill. No-ops silently if there's no recipient email
        // or no Photo field on this campaign's form -- never blocks the approval itself.
        String recipientUploadUrl = recipientUploadService.requestPhotoIfMissing(submission);

        return toResponse(submission, recipientUploadUrl);
    }

    /** Submitter-triggered, from "My submissions" -- e.g. the recipient's first photo wasn't clear enough, ask again. */
    @Transactional
    public String requestPhotoLinkAgain(UUID submissionId) {
        Submission submission = getOwnEntity(submissionId);
        if (submission.getStatus() != SubmissionStatus.APPROVED) {
            throw new SubmissionNotApprovedException(submissionId);
        }
        return recipientUploadService.requestAgain(submission);
    }

    @Transactional
    public SubmissionResponse reject(UUID submissionId, String reason) {
        Submission submission = getEntity(submissionId);
        User approver = campaignService.currentUser();
        requireReviewable(submission, approver);

        submission.setStatus(SubmissionStatus.REJECTED);
        submission.setRejectionReason(reason);
        submission.setReviewedBy(approver);
        submission.setReviewedAt(Instant.now());
        return toResponse(submission);
    }

    // --- internal ---

    private void requireReviewable(Submission submission, User approver) {
        if (submission.getCampaign().getStatus() == CampaignStatus.CLOSED) {
            throw new CampaignClosedException(submission.getCampaign().getId());
        }
        if (submission.getStatus() != SubmissionStatus.SUBMITTED && submission.getStatus() != SubmissionStatus.RESUBMITTED) {
            throw new SubmissionAlreadyReviewedException(submission.getId());
        }
        if (!canReview(submission, approver)) {
            throw new NotAuthorizedToReviewException(submission.getId());
        }
    }

    /** PRD 2.7 -- the submitter's direct manager may review, or anyone holding campaign.manage as a company-wide override. */
    private boolean canReview(Submission submission, User approver) {
        if (campaignService.canManage(approver)) {
            return true;
        }
        User submittedBy = submission.getSubmittedBy();
        return submittedBy.getReportsTo() != null && submittedBy.getReportsTo().getId().equals(approver.getId());
    }

    private void requireActive(Campaign campaign) {
        if (campaign.getStatus() == CampaignStatus.CLOSED) {
            throw new CampaignClosedException(campaign.getId());
        }
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new CampaignNotActiveException(campaign.getId());
        }
    }

    private void requireInAudience(Campaign campaign) {
        User user = campaignService.currentUser();
        if (!campaignService.canManage(user) && !campaignService.isInAudience(campaign, user.getId())) {
            throw new NotInCampaignAudienceException(campaign.getId());
        }
    }

    private String valueText(UUID submissionId, String fieldKey) {
        return submissionValueRepository.findBySubmissionIdAndCampaignFieldFieldKey(submissionId, fieldKey)
                .map(SubmissionValue::getValueText)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private void applyValues(Submission submission, UUID campaignId, Map<String, Object> values) {
        CampaignForm form = campaignFormRepository.findByCampaignId(campaignId).orElseThrow();
        List<CampaignField> fields = campaignFieldRepository.findByCampaignFormIdOrderByDisplayOrderAsc(form.getId());
        List<String> errors = new ArrayList<>();

        for (CampaignField field : fields) {
            Object raw = values.get(field.getFieldKey());
            boolean blank = raw == null || (raw instanceof String s && s.isBlank()) || (raw instanceof List<?> l && l.isEmpty());
            if (field.isRequired() && blank) {
                errors.add(field.getLabel() + " is required.");
                continue;
            }
            if (blank) {
                continue;
            }

            try {
                if (field.getFieldType() == FieldType.IMAGE || field.getFieldType() == FieldType.FILE) {
                    Map<String, Object> attachment = (Map<String, Object>) raw;
                    submissionAttachmentRepository.save(new SubmissionAttachment(
                            submission,
                            field,
                            (String) attachment.get("key"),
                            (String) attachment.get("fileName"),
                            (String) attachment.get("mimeType"),
                            ((Number) attachment.get("fileSize")).longValue()));
                    continue;
                }

                SubmissionValue value = new SubmissionValue(submission, field);
                switch (field.getFieldType()) {
                    case NUMBER -> value.setValueNumber(((Number) raw).doubleValue());
                    case BOOLEAN, CHECKBOX -> value.setValueBoolean((Boolean) raw);
                    case DATE -> value.setValueDate(LocalDate.parse((String) raw));
                    case MULTISELECT -> value.setValueList((List<String>) raw);
                    default -> value.setValueText(String.valueOf(raw));
                }
                submissionValueRepository.save(value);
            } catch (ClassCastException | NullPointerException e) {
                errors.add(field.getLabel() + " has an invalid value.");
            }
        }

        if (!errors.isEmpty()) {
            throw new SubmissionValidationException(errors);
        }
    }

    private Submission getEntity(UUID id) {
        Submission submission = submissionRepository.findById(id).orElseThrow(() -> new SubmissionNotFoundException(id));
        if (!submission.getCompany().getId().equals(campaignService.currentUser().getCompany().getId())) {
            throw new SubmissionNotFoundException(id);
        }
        return submission;
    }

    private Submission getOwnEntity(UUID id) {
        Submission submission = getEntity(id);
        if (!submission.getSubmittedBy().getId().equals(campaignService.currentUser().getId())) {
            throw new SubmissionNotFoundException(id);
        }
        return submission;
    }

    private SubmissionResponse toResponse(Submission submission) {
        return toResponse(submission, null);
    }

    private SubmissionResponse toResponse(Submission submission, String recipientUploadUrl) {
        List<SubmissionValueDto> values = submissionValueRepository.findBySubmissionId(submission.getId()).stream()
                .map(v -> new SubmissionValueDto(v.getCampaignField().getFieldKey(), v.getCampaignField().getLabel(), valueOf(v)))
                .toList();
        List<SubmissionAttachmentDto> attachments = submissionAttachmentRepository.findBySubmissionId(submission.getId()).stream()
                .map(a -> new SubmissionAttachmentDto(
                        a.getCampaignField().getFieldKey(), a.getCampaignField().getLabel(), a.getFileName(),
                        fileStorageService.publicUrl(a.getFileKey())))
                .toList();
        return SubmissionResponse.from(submission, values, attachments, recipientUploadUrl);
    }

    private Object valueOf(SubmissionValue v) {
        if (v.getValueText() != null) {
            return v.getValueText();
        }
        if (v.getValueNumber() != null) {
            return v.getValueNumber();
        }
        if (v.getValueBoolean() != null) {
            return v.getValueBoolean();
        }
        if (v.getValueDate() != null) {
            return v.getValueDate().toString();
        }
        return v.getValueList();
    }
}
