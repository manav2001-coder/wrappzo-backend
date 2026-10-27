package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.OrderDesignResponse;
import com.wrapzzo.backend.dto.PendingArtworkApprovalResponse;
import com.wrapzzo.backend.dto.PresignOrderDesignResponse;
import com.wrapzzo.backend.exception.OrderDesignAlreadyReviewedException;
import com.wrapzzo.backend.exception.OrderDesignNotFoundException;
import com.wrapzzo.backend.exception.OrderNotFoundException;
import com.wrapzzo.backend.model.CompanyFeature;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Artwork proof review loop for an order (PEL process steps 5-6: Design &amp; QC, then
 * Review &amp; rework) -- the vendor assigned to the order uploads a version, a company
 * reviewer (order.design.review) approves it or rejects it with a comment, which the
 * vendor sees and answers with the next version. Deliberately its own service rather than
 * folded into OrderService: a distinct actor pair (vendor uploads, company reviews) and a
 * distinct access-control key on each side.
 */
@Service
@RequiredArgsConstructor
public class OrderDesignService {

    /** Granted to Company Admin by default (V42): final artwork approval for any MR, not just direct reports. */
    public static final String FINAL_APPROVAL_KEY = "order.design.final";

    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final OrderDesignRepository orderDesignRepository;
    private final OrderRepository orderRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final OrgScopeService orgScopeService;
    private final CompanyFeatureFlagService companyFeatureFlagService;
    private final QuickGiftRequestRepository quickGiftRequestRepository;
    private final AccessControlCacheService accessControlCacheService;

    // --- Vendor side: only orders assigned to them. ---

    public PresignOrderDesignResponse presignForVendor(UUID orderId, UUID vendorId, String contentType) {
        Order order = getEntityForVendor(orderId, vendorId);
        String key = "order-designs/%s/%s".formatted(order.getId(), UUID.randomUUID());
        String uploadUrl = fileStorageService.presignUpload(key, contentType, UPLOAD_URL_EXPIRY);
        return new PresignOrderDesignResponse(uploadUrl, key);
    }

    @Transactional
    public OrderDesignResponse uploadForVendor(UUID orderId, UUID vendorId, String fileKey, String fileName) {
        Order order = getEntityForVendor(orderId, vendorId);
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow(() -> new OrderNotFoundException(orderId));
        int nextVersion = orderDesignRepository.countByOrder_Id(order.getId()) + 1;
        OrderDesign design = orderDesignRepository.save(new OrderDesign(order, nextVersion, fileKey, fileName, vendor));
        return toResponse(design);
    }

    @Transactional(readOnly = true)
    public List<OrderDesignResponse> listForVendor(UUID orderId, UUID vendorId) {
        Order order = getEntityForVendor(orderId, vendorId);
        return toResponseList(orderDesignRepository.findByOrder_IdOrderByVersionNoDesc(order.getId()));
    }

    // --- Company side: only orders in the reviewer's own company. ---

    @Transactional(readOnly = true)
    public List<OrderDesignResponse> listForCompany(UUID orderId) {
        Order order = getOwnCompanyEntity(orderId);
        return toResponseList(orderDesignRepository.findByOrder_IdOrderByVersionNoDesc(order.getId()));
    }

    /**
     * The MR's approval, or the senior's final one. With the company's MARKETING_DESIGN_APPROVAL
     * flag on, an MR-approved proof waits (PENDING_FINAL_APPROVAL) for the MR's manager or an
     * order.design.final holder; only APPROVED unlocks dispatch (OrderService's artwork gate).
     */
    @Transactional
    public OrderDesignResponse approve(UUID orderId, UUID designId) {
        OrderDesign design = reviewableDesign(orderId, designId);
        User reviewer = currentUser();
        Instant now = Instant.now();
        if (design.getStatus() == OrderDesignStatus.PENDING_REVIEW
                && companyFeatureFlagService.isEnabled(design.getOrder().getCompany().getId(), CompanyFeature.MARKETING_DESIGN_APPROVAL)) {
            design.setStatus(OrderDesignStatus.PENDING_FINAL_APPROVAL);
            design.setFirstApprovedBy(reviewer);
            design.setFirstApprovedAt(now);
        } else {
            design.setStatus(OrderDesignStatus.APPROVED);
            design.setReviewedBy(reviewer);
            design.setReviewedAt(now);
        }
        return toResponse(design);
    }

    /** Sends the proof back to the vendor -- from the MR's review or from the senior's. */
    @Transactional
    public OrderDesignResponse reject(UUID orderId, UUID designId, String comment) {
        OrderDesign design = reviewableDesign(orderId, designId);
        User reviewer = currentUser();
        design.setStatus(OrderDesignStatus.REJECTED);
        design.setReviewComment(comment);
        design.setReviewedBy(reviewer);
        design.setReviewedAt(Instant.now());
        return toResponse(design);
    }

    /** A senior's queue: MR-approved proofs from their direct reports, or company-wide with order.design.final. */
    @Transactional(readOnly = true)
    public List<PendingArtworkApprovalResponse> listPendingFinalApproval() {
        User reviewer = currentUser();
        if (reviewer.getCompany() == null) {
            return List.of();
        }
        return orderDesignRepository
                .findByStatusAndOrder_Company_IdOrderByUpdatedAtAsc(OrderDesignStatus.PENDING_FINAL_APPROVAL, reviewer.getCompany().getId())
                .stream()
                .filter(design -> canGiveFinalApproval(design, reviewer))
                .map(design -> PendingArtworkApprovalResponse.from(
                        design, fileStorageService.publicUrl(design.getFileKey()), requesterOf(design.getOrder())))
                .toList();
    }

    private OrderDesign reviewableDesign(UUID orderId, UUID designId) {
        OrderDesign design = orderDesignRepository.findById(designId).orElseThrow(() -> new OrderDesignNotFoundException(designId));
        if (!design.getOrder().getId().equals(orderId)) {
            throw new OrderDesignNotFoundException(designId);
        }
        switch (design.getStatus()) {
            case PENDING_REVIEW -> getOwnCompanyEntity(orderId);
            case PENDING_FINAL_APPROVAL -> {
                // The senior may sit outside the MR's org subtree checks (a manager over another
                // branch), so this is authorised by the reporting line, not OrgScope.
                User reviewer = currentUser();
                if (reviewer.getCompany() == null || !design.getOrder().getCompany().getId().equals(reviewer.getCompany().getId())) {
                    throw new OrderNotFoundException(orderId);
                }
                if (design.getFirstApprovedBy() != null && design.getFirstApprovedBy().getId().equals(reviewer.getId())) {
                    throw new AccessDeniedException("The final approval has to come from someone other than the MR who approved it");
                }
                if (!canGiveFinalApproval(design, reviewer)) {
                    throw new AccessDeniedException("Only the MR's manager can give the final approval on this artwork");
                }
            }
            default -> throw new OrderDesignAlreadyReviewedException(designId);
        }
        return design;
    }

    private boolean canGiveFinalApproval(OrderDesign design, User reviewer) {
        if (design.getFirstApprovedBy() != null && design.getFirstApprovedBy().getId().equals(reviewer.getId())) {
            return false;
        }
        User requester = requesterOf(design.getOrder());
        boolean isDirectManager = requester != null && requester.getReportsTo() != null
                && requester.getReportsTo().getId().equals(reviewer.getId());
        return isDirectManager || accessControlCacheService.resolveEffectiveKeys(reviewer).contains(FINAL_APPROVAL_KEY);
    }

    /** The MR behind an order: a Quick Gift's requester (an approved one's createdBy is its approver), else whoever created it. */
    private User requesterOf(Order order) {
        return quickGiftRequestRepository.findByOrderId(order.getId()).map(QuickGiftRequest::getRequestedBy).orElse(order.getCreatedBy());
    }

    private Order getEntityForVendor(UUID orderId, UUID vendorId) {
        Order order = getEntity(orderId);
        if (order.getAssignedVendor() == null || !order.getAssignedVendor().getId().equals(vendorId)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    /**
     * A reviewer reviews within their own org subtree (the MR who placed it always can) -- the
     * same OrgScope the Orders list uses, so "can see it" and "can review it" never disagree.
     */
    private Order getOwnCompanyEntity(UUID orderId) {
        Order order = getEntity(orderId);
        User user = currentUser();
        if (!order.getCompany().getId().equals(user.getCompany().getId())
                || !orgScopeService.resolve(user).covers(order.getOrgUnit(), order.getCreatedBy())) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    private Order getEntity(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }

    private OrderDesignResponse toResponse(OrderDesign design) {
        return OrderDesignResponse.from(design, fileStorageService.publicUrl(design.getFileKey()));
    }

    private List<OrderDesignResponse> toResponseList(List<OrderDesign> designs) {
        return designs.stream().map(this::toResponse).toList();
    }
}
