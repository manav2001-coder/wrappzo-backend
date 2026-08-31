package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.BulkAssignResponse;
import com.wrapzzo.backend.dto.OrderResponse;
import com.wrapzzo.backend.dto.VendorOrderResponse;
import com.wrapzzo.backend.exception.InvalidOrderStatusTransitionException;
import com.wrapzzo.backend.exception.NoAvailableVendorException;
import com.wrapzzo.backend.exception.OrderNotFoundException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderAssignmentMode;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order lifecycle beyond creation (status transitions past PROCESSING) is a later,
 * separate pass (WorkOrder/artwork/QC/dispatch) -- this service covers the customer-
 * facing read side plus Admin's one Fulfillment & Operations action available today:
 * routing an order to a vendor (PRD Part 1.5).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    /** An order already routed to a vendor counts toward that vendor's load for auto-assign's naive balancing. */
    private static final List<OrderStatus> IN_FLIGHT_STATUSES = List.of(OrderStatus.PROCESSING, OrderStatus.PRODUCTION_QUEUE);

    /**
     * For now, a vendor's only action is marking an order delivered -- no separate "dispatched"
     * step -- from either state it's routed to them in. Revisit if a real dispatch step is
     * needed later; this map is the one place that decision lives.
     */
    private static final Map<OrderStatus, OrderStatus> VENDOR_TRANSITIONS =
            Map.of(OrderStatus.PRODUCTION_QUEUE, OrderStatus.DELIVERED, OrderStatus.DISPATCHED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;

    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return orderRepository.findByCompanyIdOrderByCreatedAtDesc(currentUser().getCompany().getId()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        if (!order.getCompany().getId().equals(currentUser().getCompany().getId())) {
            throw new OrderNotFoundException(id);
        }
        return OrderResponse.from(order);
    }

    // --- Vendor self-service side: only orders routed to this vendor, and moving them through fulfillment. ---

    @Transactional(readOnly = true)
    public List<VendorOrderResponse> listForVendor(UUID vendorId) {
        return orderRepository.findByAssignedVendorIdOrderByCreatedAtDesc(vendorId).stream().map(VendorOrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VendorOrderResponse getForVendor(UUID id, UUID vendorId) {
        return VendorOrderResponse.from(getEntityForVendor(id, vendorId));
    }

    @Transactional
    public VendorOrderResponse updateStatusForVendor(UUID id, UUID vendorId, OrderStatus newStatus) {
        Order order = getEntityForVendor(id, vendorId);
        if (!newStatus.equals(VENDOR_TRANSITIONS.get(order.getStatus()))) {
            throw new InvalidOrderStatusTransitionException(id, order.getStatus(), newStatus);
        }
        order.setStatus(newStatus);
        return VendorOrderResponse.from(order);
    }

    private Order getEntityForVendor(UUID id, UUID vendorId) {
        Order order = getEntity(id);
        if (order.getAssignedVendor() == null || !order.getAssignedVendor().getId().equals(vendorId)) {
            throw new OrderNotFoundException(id);
        }
        return order;
    }

    // --- Admin/platform side: every company's orders, for fulfillment assignment. ---

    @Transactional(readOnly = true)
    public List<OrderResponse> listAllForPlatform() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(OrderResponse::from).toList();
    }

    @Transactional
    public OrderResponse assignVendorManually(UUID orderId, UUID vendorId) {
        Order order = getEntity(orderId);
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow(() -> new VendorNotFoundException(vendorId));
        applyAssignment(order, vendor, OrderAssignmentMode.MANUAL);
        return OrderResponse.from(order);
    }

    /**
     * Naive heuristic, not real vendor-capability matching (that's future work, see PRD 6.4
     * "Future Fulfillment"): prefer the vendor who originated this gift (Gift.vendor, set
     * when a vendor submitted it themselves); otherwise the active vendor with the fewest
     * in-flight orders right now, a simple load-balance rather than true capacity planning.
     */
    @Transactional
    public OrderResponse autoAssignVendor(UUID orderId) {
        Order order = getEntity(orderId);
        Vendor vendor = order.getGift() != null && order.getGift().getVendor() != null
                ? order.getGift().getVendor()
                : leastLoadedActiveVendor();
        applyAssignment(order, vendor, OrderAssignmentMode.AUTO);
        return OrderResponse.from(order);
    }

    /**
     * Campaign-level bulk auto-assign -- one click for every unassigned order under a
     * campaign, instead of Admin repeating the per-order Auto-assign button one at a time.
     * Best-effort: an order with no fallback vendor available is skipped, not fatal to the
     * rest of the batch.
     */
    @Transactional
    public BulkAssignResponse autoAssignCampaign(UUID campaignId) {
        int assigned = 0;
        int skipped = 0;
        for (Order order : unassignedOrders(campaignId)) {
            try {
                Vendor vendor = order.getGift() != null && order.getGift().getVendor() != null
                        ? order.getGift().getVendor()
                        : leastLoadedActiveVendor();
                applyAssignment(order, vendor, OrderAssignmentMode.AUTO);
                assigned++;
            } catch (NoAvailableVendorException e) {
                skipped++;
            }
        }
        return new BulkAssignResponse(assigned, skipped, campaignOrders(campaignId));
    }

    /** Campaign-level bulk manual assign -- routes every unassigned order under a campaign to one chosen vendor. */
    @Transactional
    public BulkAssignResponse assignCampaignVendor(UUID campaignId, UUID vendorId) {
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow(() -> new VendorNotFoundException(vendorId));
        List<Order> orders = unassignedOrders(campaignId);
        for (Order order : orders) {
            applyAssignment(order, vendor, OrderAssignmentMode.MANUAL);
        }
        return new BulkAssignResponse(orders.size(), 0, campaignOrders(campaignId));
    }

    private List<Order> unassignedOrders(UUID campaignId) {
        return orderRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .filter(order -> order.getAssignedVendor() == null && order.getStatus() != OrderStatus.CANCELLED)
                .toList();
    }

    private List<OrderResponse> campaignOrders(UUID campaignId) {
        return orderRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream().map(OrderResponse::from).toList();
    }

    private Vendor leastLoadedActiveVendor() {
        return vendorRepository.findByStatus(VendorStatus.ACTIVE).stream()
                .min(Comparator.comparingLong(v -> orderRepository.countByAssignedVendorIdAndStatusIn(v.getId(), IN_FLIGHT_STATUSES)))
                .orElseThrow(NoAvailableVendorException::new);
    }

    private void applyAssignment(Order order, Vendor vendor, OrderAssignmentMode mode) {
        order.setAssignedVendor(vendor);
        order.setAssignmentMode(mode);
        order.setAssignedAt(Instant.now());
        if (order.getStatus() == OrderStatus.PROCESSING) {
            order.setStatus(OrderStatus.PRODUCTION_QUEUE);
        }
    }

    private Order getEntity(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }
}
