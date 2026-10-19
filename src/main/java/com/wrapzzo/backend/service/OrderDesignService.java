package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.OrderDesignResponse;
import com.wrapzzo.backend.dto.PresignOrderDesignResponse;
import com.wrapzzo.backend.exception.OrderDesignAlreadyReviewedException;
import com.wrapzzo.backend.exception.OrderDesignNotFoundException;
import com.wrapzzo.backend.exception.OrderNotFoundException;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final OrderDesignRepository orderDesignRepository;
    private final OrderRepository orderRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

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

    @Transactional
    public OrderDesignResponse approve(UUID orderId, UUID designId) {
        OrderDesign design = reviewableDesign(orderId, designId);
        User reviewer = currentUser();
        design.setStatus(OrderDesignStatus.APPROVED);
        design.setReviewedBy(reviewer);
        design.setReviewedAt(Instant.now());
        return toResponse(design);
    }

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

    private OrderDesign reviewableDesign(UUID orderId, UUID designId) {
        getOwnCompanyEntity(orderId);
        OrderDesign design = orderDesignRepository.findById(designId).orElseThrow(() -> new OrderDesignNotFoundException(designId));
        if (!design.getOrder().getId().equals(orderId)) {
            throw new OrderDesignNotFoundException(designId);
        }
        if (design.getStatus() != OrderDesignStatus.PENDING_REVIEW) {
            throw new OrderDesignAlreadyReviewedException(designId);
        }
        return design;
    }

    private Order getEntityForVendor(UUID orderId, UUID vendorId) {
        Order order = getEntity(orderId);
        if (order.getAssignedVendor() == null || !order.getAssignedVendor().getId().equals(vendorId)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    private Order getOwnCompanyEntity(UUID orderId) {
        Order order = getEntity(orderId);
        if (!order.getCompany().getId().equals(currentUser().getCompany().getId())) {
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
