package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.PresignQuickGiftPhotoResponse;
import com.wrapzzo.backend.dto.QuickGiftRequestResponse;
import com.wrapzzo.backend.dto.RecipientResponse;
import com.wrapzzo.backend.exception.GiftNotAvailableForQuickGiftException;
import com.wrapzzo.backend.exception.GiftNotFoundException;
import com.wrapzzo.backend.exception.PhotoOrEmailRequiredException;
import com.wrapzzo.backend.exception.QuickGiftAlreadyReviewedException;
import com.wrapzzo.backend.exception.QuickGiftNotApprovedException;
import com.wrapzzo.backend.exception.QuickGiftRequestNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftModule;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quick Gift (PRD Part 2, quick path) -- pick a gift from the shared Gift Store, fill in the
 * recipient, and send it for approval: same Company Admin approval gate as a Campaign
 * submission (SUBMITTED -> APPROVED/REJECTED), just without a campaign or an audience to
 * belong to. An approved request becomes an ordinary Order (source = QUICK_GIFT).
 */
@Service
@RequiredArgsConstructor
public class QuickGiftService {

    private static final Duration PHOTO_UPLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final QuickGiftRequestRepository quickGiftRequestRepository;
    private final GiftRepository giftRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final QuickGiftUploadService quickGiftUploadService;
    private final RecipientService recipientService;
    private final OrderStatusHistoryService orderStatusHistoryService;

    public PresignQuickGiftPhotoResponse presignPhoto(String contentType) {
        User user = currentUser();
        String key = "quick-gifts/%s/%s/%s".formatted(user.getCompany().getId(), user.getId(), UUID.randomUUID());
        String uploadUrl = fileStorageService.presignUpload(key, contentType, PHOTO_UPLOAD_URL_EXPIRY);
        return new PresignQuickGiftPhotoResponse(uploadUrl, key);
    }

    @Transactional
    public QuickGiftRequestResponse create(CreateQuickGiftRequest request) {
        User requester = currentUser();
        Company company = requester.getCompany();
        Gift gift = resolveQuickGiftEligibleGift(request.giftId());

        // An existing doctor was picked (recipientId set) -- trust that record's own fields,
        // not whatever the client happened to send alongside the id. A new doctor
        // (recipientId null) is described entirely by the free-text fields, as before.
        String name = request.recipientName();
        String phone = request.recipientPhone();
        String email = request.recipientEmail();
        String address = request.deliveryAddress();
        LocalDate dob = request.recipientDob();
        if (request.recipientId() != null) {
            RecipientResponse recipient = recipientService.get(request.recipientId());
            name = recipient.name();
            phone = recipient.phone();
            email = recipient.email();
            address = recipient.address() != null ? recipient.address() : request.deliveryAddress();
            dob = recipient.dateOfBirth();
        }

        boolean hasEmail = email != null && !email.isBlank();
        if (gift.isPhotoRequired() && request.photo() == null && !hasEmail) {
            throw new PhotoOrEmailRequiredException();
        }

        QuickGiftRequest quickGift =
                new QuickGiftRequest(company, gift, requester, name, phone, email, address, request.giftMessage(), request.internalNotes());
        quickGift.setRecipientDob(dob);
        if (request.photo() != null) {
            quickGift.setPhotoKey(request.photo().key());
            quickGift.setPhotoFileName(request.photo().fileName());
            quickGift.setPhotoContentType(request.photo().contentType());
        }
        quickGift = quickGiftRequestRepository.saveAndFlush(quickGift);
        recipientService.upsertFromSubmission(company, name, phone, email, address, dob, requester);
        return toResponse(quickGift);
    }

    @Transactional(readOnly = true)
    public List<QuickGiftRequestResponse> listMine() {
        User user = currentUser();
        return quickGiftRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuickGiftRequestResponse> listPendingApproval() {
        User approver = currentUser();
        return quickGiftRequestRepository
                .findByCompanyIdAndStatusOrderByCreatedAtDesc(approver.getCompany().getId(), QuickGiftStatus.SUBMITTED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuickGiftRequestResponse get(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public QuickGiftRequestResponse approve(UUID id) {
        QuickGiftRequest request = getEntity(id);
        requireReviewable(request);
        User approver = currentUser();

        // saveAndFlush -- orderNo is DB-generated and only readable after the physical INSERT runs.
        Order order = orderRepository.saveAndFlush(new Order(
                request.getCompany(), request.getGift(), OrderSource.QUICK_GIFT, request.getRecipientName(),
                request.getRecipientPhone(), approver));
        orderStatusHistoryService.record(order, OrderStatus.PROCESSING, approver);
        request.setOrder(order);
        request.setStatus(QuickGiftStatus.APPROVED);
        request.setReviewedBy(approver);
        request.setReviewedAt(Instant.now());

        // Best-effort: if no photo was attached at request time, hand the recipient a
        // self-serve upload link now that there's a real Order to fulfill. Never blocks approval.
        String recipientUploadUrl = quickGiftUploadService.requestPhotoIfMissing(request);

        String photoUrl = request.getPhotoKey() != null ? fileStorageService.publicUrl(request.getPhotoKey()) : null;
        return QuickGiftRequestResponse.from(request, photoUrl, recipientUploadUrl);
    }

    @Transactional
    public QuickGiftRequestResponse reject(UUID id, String reason) {
        QuickGiftRequest request = getEntity(id);
        requireReviewable(request);
        User approver = currentUser();

        request.setStatus(QuickGiftStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setReviewedBy(approver);
        request.setReviewedAt(Instant.now());
        return toResponse(request);
    }

    /** Requester-triggered, from "My Quick Gifts" -- e.g. the recipient's first photo wasn't clear enough. */
    @Transactional
    public String requestPhotoLinkAgain(UUID id) {
        QuickGiftRequest request = getOwnEntity(id);
        if (request.getStatus() != QuickGiftStatus.APPROVED) {
            throw new QuickGiftNotApprovedException(id);
        }
        return quickGiftUploadService.requestAgain(request);
    }

    private void requireReviewable(QuickGiftRequest request) {
        if (request.getStatus() != QuickGiftStatus.SUBMITTED) {
            throw new QuickGiftAlreadyReviewedException(request.getId());
        }
    }

    private Gift resolveQuickGiftEligibleGift(UUID giftId) {
        Gift gift = giftRepository.findById(giftId).orElseThrow(() -> new GiftNotFoundException(giftId));
        if (!gift.isActive() || !gift.getModules().contains(GiftModule.QUICK_GIFT)) {
            throw new GiftNotAvailableForQuickGiftException(giftId);
        }
        return gift;
    }

    private QuickGiftRequest getEntity(UUID id) {
        QuickGiftRequest request = quickGiftRequestRepository.findById(id)
                .orElseThrow(() -> new QuickGiftRequestNotFoundException(id));
        if (!request.getCompany().getId().equals(currentUser().getCompany().getId())) {
            throw new QuickGiftRequestNotFoundException(id);
        }
        return request;
    }

    private QuickGiftRequest getOwnEntity(UUID id) {
        QuickGiftRequest request = getEntity(id);
        if (!request.getRequestedBy().getId().equals(currentUser().getId())) {
            throw new QuickGiftRequestNotFoundException(id);
        }
        return request;
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }

    private QuickGiftRequestResponse toResponse(QuickGiftRequest request) {
        String photoUrl = request.getPhotoKey() != null ? fileStorageService.publicUrl(request.getPhotoKey()) : null;
        return QuickGiftRequestResponse.from(request, photoUrl);
    }
}
