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
import com.wrapzzo.backend.exception.NotInCampaignAudienceException;
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
import com.wrapzzo.backend.model.OrderStatus;
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
    private final RecipientService recipientService;
    private final OrderStatusHistoryService orderStatusHistoryService;
    private final WarehouseService warehouseService;
    private final VisibilityService visibilityService;

    public PresignSubmissionAttachmentResponse presignAttachment(UUID campaignId, String contentType) {
        Campaign campaign = campaignService.getEntity(campaignId);
        requireActive(campaign);
        requireInAudience(campaign);
        String key = "submissions/%s/%s/%s".formatted(campaignId, campaignService.currentUser().getId(), UUID.randomUUID());
        String uploadUrl = fileStorageService.presignUpload(key, contentType, ATTACHMENT_UPLOAD_URL_EXPIRY);
        return new PresignSubmissionAttachmentResponse(uploadUrl, key);
    }

    /**
     * No approval gate -- a submitted recipient becomes an Order immediately (approval is
     * Quick Gift-only now). Mirrors what the old approve() step used to do: mark it
     * approved, create the Order, and request the recipient's photo if the submission
     * didn't already carry one.
     */
    @Transactional
    public SubmissionResponse create(UUID campaignId, CreateSubmissionRequest request) {
        Campaign campaign = campaignService.getEntity(campaignId);
        requireActive(campaign);
        requireInAudience(campaign);
        Gift gift = resolveOfferedGift(campaign, request.giftId());

        User submitter = campaignService.currentUser();
        Submission submission = submissionRepository.save(new Submission(campaign.getCompany(), campaign, submitter, gift));
        applyValues(submission, campaignId, gift, request.values());
        Instant now = Instant.now();
        submission.setSubmittedAt(now);
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedAt(now);
        upsertRecipientBestEffort(submission);

        String recipientName = valueText(submission.getId(), "recipientName");
        String recipientPhone = valueText(submission.getId(), "recipientPhone");
        Order order = new Order(submission.getCompany(), submission.getCampaign(), submission, submission.getGift(), recipientName, recipientPhone, submitter);
        order.setOrgUnit(submitter.getOrgUnit());
        warehouseService.routeOnPlacement(order);
        orderRepository.save(order);
        orderStatusHistoryService.record(order, OrderStatus.PROCESSING, submitter);

        // Best-effort: if this recipient wasn't given a photo at submission time (the
        // default Photo field is optional), hand them a self-serve upload link now that
        // there's a real Order to fulfill. No-ops silently if there's no recipient email
        // or no Photo field on this campaign's form -- never blocks the submission itself.
        String recipientUploadUrl = recipientUploadService.requestPhotoIfMissing(submission);

        return toResponse(submission, recipientUploadUrl);
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
        Visibility visibility = visibilityService.resolve(campaignService.currentUser());
        return submissionRepository
                .findVisibleByCampaign(campaignId, visibility.companyWide(), visibility.peopleForQuery(), visibility.orgUnitsForQuery())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> listMine() {
        User user = campaignService.currentUser();
        return submissionRepository.findBySubmittedByIdOrderByCreatedAtDesc(user.getId()).stream().map(this::toResponse).toList();
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

    // --- internal ---

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
        if (!campaignService.canEditCampaigns(user) && !campaignService.isInAudience(campaign, user.getId())) {
            throw new NotInCampaignAudienceException(campaign.getId());
        }
    }

    private String valueText(UUID submissionId, String fieldKey) {
        return submissionValueRepository.findBySubmissionIdAndCampaignFieldFieldKey(submissionId, fieldKey)
                .map(SubmissionValue::getValueText)
                .orElse(null);
    }

    private LocalDate valueDate(UUID submissionId, String fieldKey) {
        return submissionValueRepository.findBySubmissionIdAndCampaignFieldFieldKey(submissionId, fieldKey)
                .map(SubmissionValue::getValueDate)
                .orElse(null);
    }

    /**
     * Best-effort: matches the default field keys (recipientName/Phone/Email/Address/Dob)
     * seen by DefaultCampaignFields -- a campaign creator who renamed or removed those
     * fields just means this quietly finds nothing to add, never blocks the submission.
     */
    private void upsertRecipientBestEffort(Submission submission) {
        String name = valueText(submission.getId(), "recipientName");
        String phone = valueText(submission.getId(), "recipientPhone");
        if (name == null || phone == null) {
            return;
        }
        String email = valueText(submission.getId(), "recipientEmail");
        String address = valueText(submission.getId(), "recipientAddress");
        LocalDate dob = valueDate(submission.getId(), "recipientDob");
        recipientService.upsertFromSubmission(submission.getCompany(), name, phone, email, address, dob, submission.getSubmittedBy());
    }

    @SuppressWarnings("unchecked")
    private void applyValues(Submission submission, UUID campaignId, Gift gift, Map<String, Object> values) {
        CampaignForm form = campaignFormRepository.findByCampaignId(campaignId).orElseThrow();
        List<CampaignField> fields = campaignFieldRepository.findByCampaignFormIdOrderByDisplayOrderAsc(form.getId());
        List<String> errors = new ArrayList<>();

        // Tracked alongside the per-field required checks below, so a gift that demands a
        // photo can be satisfied either way -- mirrors Quick Gift's same rule. Skipped
        // entirely (never blocks) if this campaign's form doesn't have both fields under
        // their default keys, same "quietly finds nothing to check" philosophy as
        // upsertRecipientBestEffort/requestPhotoIfMissing for a renamed/removed field.
        boolean hasPhotoField = fields.stream().anyMatch(f -> "photo".equals(f.getFieldKey()));
        boolean hasEmailField = fields.stream().anyMatch(f -> "recipientEmail".equals(f.getFieldKey()));
        boolean photoProvided = false;
        boolean emailProvided = false;

        for (CampaignField field : fields) {
            Object raw = values.get(field.getFieldKey());
            boolean blank = raw == null || (raw instanceof String s && s.isBlank()) || (raw instanceof List<?> l && l.isEmpty());
            if ("photo".equals(field.getFieldKey()) && !blank) {
                photoProvided = true;
            }
            if ("recipientEmail".equals(field.getFieldKey()) && !blank) {
                emailProvided = true;
            }
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

        if (gift.isPhotoRequired() && hasPhotoField && hasEmailField && !photoProvided && !emailProvided) {
            errors.add("This gift needs a recipient photo -- upload one, or provide the recipient's email so we can send them an upload link.");
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
