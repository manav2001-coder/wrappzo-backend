package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.PhotoResponse;
import java.util.List;
import com.wrapzzo.backend.exception.OrderNotFoundException;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.SubmissionAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves and (re)requests a recipient's photo from an Order, regardless of whether it came
 * from a Campaign submission (an EAV SubmissionAttachment) or a Quick Gift request (a plain
 * column) -- Order itself carries neither directly. Order-facing surfaces (the company Orders
 * page, the platform admin Orders page, the Vendor's own order view) all go through here
 * instead of duplicating the CAMPAIGN-vs-QUICK_GIFT branch three times.
 */
@Service
@RequiredArgsConstructor
public class OrderPhotoService {

    private static final String PHOTO_FIELD_KEY = "photo";

    private final SubmissionAttachmentRepository submissionAttachmentRepository;
    private final QuickGiftRequestRepository quickGiftRequestRepository;
    private final FileStorageService fileStorageService;
    private final RecipientUploadService recipientUploadService;
    private final QuickGiftUploadService quickGiftUploadService;

    /** Empty if no photo has been uploaded yet (or this order has no gift-personalization photo to speak of). */
    @Transactional(readOnly = true)
    public List<PhotoResponse> resolvePhotos(Order order) {
        if (order.getSource() == OrderSource.CAMPAIGN && order.getSubmission() != null) {
            return submissionAttachmentRepository.findBySubmissionId(order.getSubmission().getId()).stream()
                    .filter(a -> PHOTO_FIELD_KEY.equals(a.getCampaignField().getFieldKey()))
                    .findFirst()
                    .map(a -> new PhotoResponse(fileStorageService.publicUrl(a.getFileKey()), FileNaming.lastSegment(a.getFileKey())))
                    .stream()
                    .toList();
        }
        return quickGiftRequestRepository.findByOrderId(order.getId())
                .map(request -> request.getPhotos().stream()
                        .map(photo -> new PhotoResponse(fileStorageService.publicUrl(photo.getPhotoKey()), photo.getFileName()))
                        .toList())
                .orElse(List.of());
    }

    /**
     * (Re)sends the recipient's self-upload link for this order -- reachable by anyone with
     * order-viewing access, not just the original submitter/requester (unlike
     * Submission/QuickGiftService's own requestPhotoLinkAgain), since there's no separate
     * approval step left to gate this on and a missing photo is now something anyone tracking
     * fulfillment needs to be able to chase.
     */
    @Transactional
    public String requestPhotoLink(Order order) {
        if (order.getSource() == OrderSource.CAMPAIGN && order.getSubmission() != null) {
            return recipientUploadService.requestAgain(order.getSubmission());
        }
        QuickGiftRequest request = quickGiftRequestRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new OrderNotFoundException(order.getId()));
        return quickGiftUploadService.requestAgain(request);
    }
}
