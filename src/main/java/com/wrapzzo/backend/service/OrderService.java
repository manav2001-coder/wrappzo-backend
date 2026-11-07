package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.BulkAssignResponse;
import com.wrapzzo.backend.dto.DashboardResponse.StageEvent;
import com.wrapzzo.backend.dto.OrderResponse;
import com.wrapzzo.backend.dto.OrderSummaryResponse;
import com.wrapzzo.backend.list.ListQuery;
import com.wrapzzo.backend.list.ListSpecs;
import com.wrapzzo.backend.list.PageResponse;
import org.springframework.data.domain.Sort;
import com.wrapzzo.backend.dto.PhotoResponse;
import com.wrapzzo.backend.dto.VendorOrderResponse;
import com.wrapzzo.backend.exception.ArtworkNotApprovedException;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.InvalidOrderStatusTransitionException;
import com.wrapzzo.backend.exception.NoAvailableVendorException;
import com.wrapzzo.backend.exception.OrderNotFoundException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.OrderApprovalStatus;
import com.wrapzzo.backend.model.OrderAssignmentMode;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.OrderStatusHistory;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftImage;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.GiftImageRepository;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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

    /** A vendor can freely move an order between these -- picked directly from a dropdown, not a forced sequence. */
    private static final Set<OrderStatus> VENDOR_SELECTABLE_STATUSES =
            Set.of(OrderStatus.PRODUCTION_QUEUE, OrderStatus.DISPATCHED, OrderStatus.DELIVERED);

    /** Once an order leaves these (delivered, cancelled, or still unassigned/PROCESSING), it's no longer the vendor's to edit. */
    private static final Set<OrderStatus> VENDOR_EDITABLE_SOURCE_STATUSES = Set.of(OrderStatus.PRODUCTION_QUEUE, OrderStatus.DISPATCHED);

    /** Statuses that mean "it has left the vendor" -- closed to an artwork-required order until a proof is approved. */
    private static final Set<OrderStatus> ARTWORK_GATED_STATUSES = Set.of(OrderStatus.DISPATCHED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final VendorIdentityPolicy vendorIdentityPolicy;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final QuickGiftRequestRepository quickGiftRequestRepository;
    private final VisibilityService visibilityService;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final CompanyVendorRepository companyVendorRepository;
    private final OrderStatusHistoryService orderStatusHistoryService;
    private final OrderPhotoService orderPhotoService;
    private final GiftImageRepository giftImageRepository;
    private final OrderDesignRepository orderDesignRepository;
    private final FileStorageService fileStorageService;

    /** One page of the orders the caller can see (see Visibility), searched, filtered and sorted in the database. */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> page(ListQuery query) {
        User user = currentUser();
        return ListSpecs.page(orderRepository, OrderListFields.visibleIn(user.getCompany().getId(), visibilityService.resolve(user)), query,
                OrderListFields.of(vendorIdentityPolicy.canSee(), false), Sort.by(Sort.Direction.DESC, "createdAt"), this::toResponseList);
    }

    /** The figures over the same orders: counted in the database, whatever page the list is on. */
    @Transactional(readOnly = true)
    public OrderSummaryResponse summary() {
        User user = currentUser();
        Visibility visibility = visibilityService.resolve(user);
        UUID companyId = user.getCompany().getId();
        Map<OrderStatus, Long> byStatus = tally(orderRepository.countVisibleByStatus(companyId, visibility.companyWide(), visibility.peopleForQuery(), visibility.orgUnitsForQuery()));
        long awaitingReview = orderRepository.countVisibleAwaitingReview(companyId, visibility.companyWide(), visibility.peopleForQuery(), visibility.orgUnitsForQuery());
        return new OrderSummaryResponse(byStatus.values().stream().mapToLong(Long::longValue).sum(), byStatus, awaitingReview, 0);
    }

    private static Map<OrderStatus, Long> tally(List<Object[]> rows) {
        Map<OrderStatus, Long> byStatus = new java.util.EnumMap<>(OrderStatus.class);
        for (Object[] row : rows) {
            byStatus.put((OrderStatus) row[0], (Long) row[1]);
        }
        return byStatus;
    }

    /** One page of every company's orders for Wrappzo staff -- narrow it to a company with the companyId filter. */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> pageForPlatform(ListQuery query) {
        return ListSpecs.page(orderRepository, (root, q, cb) -> cb.conjunction(), query, OrderListFields.of(true, true),
                Sort.by(Sort.Direction.DESC, "createdAt"), this::toResponseList);
    }

    @Transactional(readOnly = true)
    public OrderResponse getForPlatform(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public OrderSummaryResponse summaryForPlatform(UUID companyId) {
        Map<OrderStatus, Long> byStatus = tally(orderRepository.countByStatusInCompany(companyId));
        return new OrderSummaryResponse(byStatus.values().stream().mapToLong(Long::longValue).sum(), byStatus, 0, orderRepository.countUnassignedInCompany(companyId));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return toResponse(getOwnCompanyEntity(id));
    }

    /** Reachable by anyone who can already see this order (list()/get() share the same company scoping). */
    @Transactional
    public String requestPhotoLink(UUID id) {
        return orderPhotoService.requestPhotoLink(getOwnCompanyEntity(id));
    }

    /** 404 for another company's order and for one the caller can't see alike. */
    private Order getOwnCompanyEntity(UUID id) {
        Order order = getEntity(id);
        User user = currentUser();
        if (!order.getCompany().getId().equals(user.getCompany().getId())
                || !visibilityService.resolve(user).canSee(order.getOwner(), order.getOrgUnit())) {
            throw new OrderNotFoundException(id);
        }
        return order;
    }

    /**
     * Direct vendor ordering: the requester already chose the vendor (by choosing that vendor's
     * gift), so the order is routed on creation -- same bookkeeping as an admin's manual assign.
     */
    @Transactional
    public void assignOnPlacement(Order order, Vendor vendor) {
        applyAssignment(order, vendor, OrderAssignmentMode.MANUAL);
    }

    // --- Vendor self-service side: only orders routed to this vendor, and moving them through fulfillment. ---

    /** Each order's originating request and its proofs are fetched in one query apiece, not one per order. */
    @Transactional(readOnly = true)
    public List<VendorOrderResponse> listForVendor(UUID vendorId) {
        List<Order> orders = orderRepository.findByAssignedVendorIdOrderByCreatedAtDesc(vendorId);
        List<UUID> orderIds = orders.stream().map(Order::getId).toList();
        Map<UUID, QuickGiftRequest> requestByOrderId = quickGiftRequestRepository.findByOrder_IdIn(orderIds).stream()
                .collect(Collectors.toMap(r -> r.getOrder().getId(), r -> r));
        Map<UUID, List<OrderDesign>> designsByOrderId = orderDesignRepository.findByOrder_IdInOrderByVersionNoDesc(orderIds).stream()
                .collect(Collectors.groupingBy(d -> d.getOrder().getId()));
        Map<UUID, OrgUnit> divisions = new HashMap<>();
        return orders.stream()
                .map(o -> VendorOrderResponse.from(
                        o, orderPhotoService.resolvePhotos(o), requestByOrderId.get(o.getId()), designsByOrderId.getOrDefault(o.getId(), List.of()),
                        divisionOf(o, divisions)))
                .toList();
    }

    @Transactional(readOnly = true)
    public VendorOrderResponse getForVendor(UUID id, UUID vendorId) {
        return toVendorResponse(getEntityForVendor(id, vendorId));
    }

    @Transactional
    public VendorOrderResponse updateStatusForVendor(UUID id, UUID vendorId, OrderStatus newStatus) {
        Order order = getEntityForVendor(id, vendorId);
        // A warehouse-routed order is delivered by the warehouse (WarehouseService), not the vendor.
        boolean viaWarehouse = order.getWarehouse() != null;
        if (!VENDOR_EDITABLE_SOURCE_STATUSES.contains(order.getStatus())
                || !VENDOR_SELECTABLE_STATUSES.contains(newStatus)
                || (viaWarehouse && newStatus == OrderStatus.DELIVERED)) {
            throw new InvalidOrderStatusTransitionException(id, order.getStatus(), newStatus);
        }
        if (ARTWORK_GATED_STATUSES.contains(newStatus) && requiresArtwork(order) && !hasApprovedArtwork(order)) {
            throw new ArtworkNotApprovedException(id);
        }
        order.setStatus(newStatus);
        // null changedBy -- this runs under vendor auth (CurrentVendor), not CurrentUser, and
        // OrderStatusHistory.changedBy is a User FK; there's no User row for a vendor to record here.
        orderStatusHistoryService.record(order, newStatus, null);
        return toVendorResponse(order);
    }

    private static boolean requiresArtwork(Order order) {
        return order.getGift() != null && order.getGift().isArtworkRequired();
    }

    private boolean hasApprovedArtwork(Order order) {
        return orderDesignRepository.existsByOrder_IdAndStatus(order.getId(), OrderDesignStatus.APPROVED);
    }

    private VendorOrderResponse toVendorResponse(Order order) {
        QuickGiftRequest request = quickGiftRequestRepository.findByOrderId(order.getId()).orElse(null);
        List<OrderDesign> designs = orderDesignRepository.findByOrder_IdOrderByVersionNoDesc(order.getId());
        return VendorOrderResponse.from(order, orderPhotoService.resolvePhotos(order), request, designs, divisionOf(order, new HashMap<>()));
    }

    /** The division (level-1 org unit) an order was raised in: its own unit, else its owner's; null when placed nowhere. */
    private OrgUnit divisionOf(Order order, Map<UUID, OrgUnit> cache) {
        OrgUnit unit = order.getOrgUnit() != null ? order.getOrgUnit() : order.getOwner().getOrgUnit();
        if (unit == null || unit.getAncestorIds().isEmpty()) {
            return unit;
        }
        UUID topId = unit.getAncestorIds().get(0);
        return cache.computeIfAbsent(topId, id -> orgUnitRepository.findById(id).orElse(unit));
    }

    private Order getEntityForVendor(UUID id, UUID vendorId) {
        Order order = getEntity(id);
        if (order.getAssignedVendor() == null || !order.getAssignedVendor().getId().equals(vendorId)) {
            throw new OrderNotFoundException(id);
        }
        return order;
    }

    // --- Admin/platform side: every company's orders, for fulfillment assignment. ---

    /** Unscoped by company -- platform admin can chase a photo for any order. */
    @Transactional
    public String requestPhotoLinkForPlatform(UUID id) {
        return orderPhotoService.requestPhotoLink(getEntity(id));
    }

    @Transactional
    public OrderResponse assignVendorManually(UUID orderId, UUID vendorId) {
        Order order = getEntity(orderId);
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow(() -> new VendorNotFoundException(vendorId));
        requireCanAssign(order, vendor);
        applyAssignment(order, vendor, OrderAssignmentMode.MANUAL);
        return toResponse(order);
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
        requireApproved(order);
        applyAssignment(order, autoVendorFor(order), OrderAssignmentMode.AUTO);
        return toResponse(order);
    }

    /**
     * Campaign-level bulk auto-assign -- one click for every unassigned order under a
     * campaign, instead of Admin repeating the per-order Auto-assign button one at a time.
     * Best-effort: an order with no fallback vendor available is skipped, not fatal to the
     * rest of the batch.
     */
    @Transactional
    public BulkAssignResponse autoAssignCampaign(UUID campaignId) {
        return bulkAutoAssign(unassignedOrders(campaignId), () -> campaignOrders(campaignId));
    }

    /** Campaign-level bulk manual assign -- routes every unassigned order under a campaign to one chosen vendor. */
    @Transactional
    public BulkAssignResponse assignCampaignVendor(UUID campaignId, UUID vendorId) {
        return bulkAssign(unassignedOrders(campaignId), vendorId, () -> campaignOrders(campaignId));
    }

    /** Same bulk auto-assign, but for a company's Quick Gift orders (no campaign to key off). */
    @Transactional
    public BulkAssignResponse autoAssignQuickGift(UUID companyId) {
        return bulkAutoAssign(unassignedQuickGiftOrders(companyId), () -> quickGiftOrders(companyId));
    }

    @Transactional
    public BulkAssignResponse assignQuickGiftVendor(UUID companyId, UUID vendorId) {
        return bulkAssign(unassignedQuickGiftOrders(companyId), vendorId, () -> quickGiftOrders(companyId));
    }

    private BulkAssignResponse bulkAutoAssign(List<Order> targets, Supplier<List<OrderResponse>> resultLoader) {
        int assigned = 0;
        int skipped = 0;
        for (Order order : targets) {
            try {
                applyAssignment(order, autoVendorFor(order), OrderAssignmentMode.AUTO);
                assigned++;
            } catch (NoAvailableVendorException e) {
                skipped++;
            }
        }
        return new BulkAssignResponse(assigned, skipped, resultLoader.get());
    }

    private BulkAssignResponse bulkAssign(List<Order> targets, UUID vendorId, Supplier<List<OrderResponse>> resultLoader) {
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow(() -> new VendorNotFoundException(vendorId));
        for (Order order : targets) {
            requireCanAssign(order, vendor);
        }
        for (Order order : targets) {
            applyAssignment(order, vendor, OrderAssignmentMode.MANUAL);
        }
        return new BulkAssignResponse(targets.size(), 0, resultLoader.get());
    }

    private List<Order> unassignedOrders(UUID campaignId) {
        return filterUnassigned(orderRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId));
    }

    private List<Order> unassignedQuickGiftOrders(UUID companyId) {
        return filterUnassigned(orderRepository.findByCompanyIdAndCampaignIdIsNullOrderByCreatedAtDesc(companyId));
    }

    private List<Order> filterUnassigned(List<Order> orders) {
        return orders.stream()
                .filter(order -> order.getAssignedVendor() == null && order.getStatus() != OrderStatus.CANCELLED
                        && order.getApprovalStatus() != OrderApprovalStatus.PENDING)
                .toList();
    }

    private List<OrderResponse> campaignOrders(UUID campaignId) {
        return toResponseList(orderRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId));
    }

    private List<OrderResponse> quickGiftOrders(UUID companyId) {
        return toResponseList(orderRepository.findByCompanyIdAndCampaignIdIsNullOrderByCreatedAtDesc(companyId));
    }

    private OrderResponse toResponse(Order order) {
        String giftImageUrl = order.getGift() != null ? firstImageUrlByGiftId(List.of(order.getGift().getId())).get(order.getGift().getId()) : null;
        boolean pending = !orderDesignRepository.findByOrder_IdInAndStatus(List.of(order.getId()), OrderDesignStatus.PENDING_REVIEW).isEmpty();
        return OrderResponse.from(order, orderPhotoService.resolvePhotos(order), giftImageUrl, stageEventsFor(order.getId()), pending,
                vendorIdentityPolicy.canSee());
    }

    private List<OrderResponse> toResponseList(List<Order> orders) {
        Map<UUID, List<OrderStatusHistory>> historyByOrderId =
                orderStatusHistoryService.historyByOrderId(orders.stream().map(Order::getId).toList());
        List<UUID> giftIds = orders.stream().map(Order::getGift).filter(Objects::nonNull).map(Gift::getId).toList();
        Map<UUID, String> firstImageUrlByGiftId = firstImageUrlByGiftId(giftIds);
        Set<UUID> ordersPendingDesignReview = orderDesignRepository
                .findByOrder_IdInAndStatus(orders.stream().map(Order::getId).toList(), OrderDesignStatus.PENDING_REVIEW)
                .stream()
                .map(d -> d.getOrder().getId())
                .collect(Collectors.toSet());
        boolean showVendor = vendorIdentityPolicy.canSee();
        return orders.stream()
                .map(o -> OrderResponse.from(
                        o,
                        orderPhotoService.resolvePhotos(o),
                        o.getGift() != null ? firstImageUrlByGiftId.get(o.getGift().getId()) : null,
                        toStageEvents(historyByOrderId.getOrDefault(o.getId(), List.of())),
                        ordersPendingDesignReview.contains(o.getId()),
                        showVendor))
                .toList();
    }

    private Map<UUID, String> firstImageUrlByGiftId(List<UUID> giftIds) {
        Map<UUID, String> map = new HashMap<>();
        for (GiftImage image : giftImageRepository.findByGiftIdInOrderByDisplayOrderAsc(giftIds)) {
            map.putIfAbsent(image.getGift().getId(), fileStorageService.publicUrl(image.getImageKey()));
        }
        return map;
    }

    private List<StageEvent> stageEventsFor(UUID orderId) {
        return toStageEvents(orderStatusHistoryService.historyByOrderId(List.of(orderId)).getOrDefault(orderId, List.of()));
    }

    private List<StageEvent> toStageEvents(List<OrderStatusHistory> history) {
        return history.stream()
                .map(h -> new StageEvent(h.getStatus(), h.getChangedAt(), h.getChangedBy() != null ? h.getChangedBy().getName() : null))
                .toList();
    }

    /** The gift's own vendor when it serves the order's company and is active; otherwise the least loaded of that company's vendors. */
    private Vendor autoVendorFor(Order order) {
        Vendor giftVendor = order.getGift() != null ? order.getGift().getVendor() : null;
        if (giftVendor != null && giftVendor.getStatus() == VendorStatus.ACTIVE && servesOrder(giftVendor, order)) {
            return giftVendor;
        }
        return leastLoadedActiveVendorOf(order);
    }

    /** Only vendors mapped to the order's company are candidates: one company must not be able to route another's orders to a vendor of its own making. */
    private Vendor leastLoadedActiveVendorOf(Order order) {
        return vendorRepository.findAllById(vendorIdsServing(order)).stream()
                .filter(vendor -> vendor.getStatus() == VendorStatus.ACTIVE)
                .min(Comparator.comparingLong(v -> orderRepository.countByAssignedVendorIdAndStatusIn(v.getId(), IN_FLIGHT_STATUSES)))
                .orElseThrow(() -> new NoAvailableVendorException(unitOf(order)));
    }

    private boolean servesCompany(Vendor vendor, Order order) {
        return companyVendorRepository.existsByCompany_IdAndVendor_Id(order.getCompany().getId(), vendor.getId());
    }

    /** The org unit the order belongs to -- its owner's (the AM's): where a vendor has to be mapped to serve it. Null for an owner with no unit. */
    private static OrgUnit unitOf(Order order) {
        return order.getOwner() != null ? order.getOwner().getOrgUnit() : null;
    }

    /**
     * The vendors that serve this order: those mapped to the whole company, or to the owner's unit or any unit above it
     * (so a vendor mapped to a division serves every order raised in that division). An owner with no unit -- a
     * company-wide user -- can use any vendor of the company.
     */
    private List<UUID> vendorIdsServing(Order order) {
        UUID companyId = order.getCompany().getId();
        OrgUnit unit = unitOf(order);
        if (unit == null) {
            return companyVendorRepository.findVendorIdsByCompany(companyId);
        }
        List<UUID> scope = new ArrayList<>(unit.getAncestorIds());
        scope.add(unit.getId());
        return companyVendorRepository.findVendorIdsVisibleToOrgUnits(companyId, scope);
    }

    private boolean servesOrder(Vendor vendor, Order order) {
        return vendorIdsServing(order).contains(vendor.getId());
    }

    /** A hand-picked vendor must be active, serve the order's company, and the order must still be waiting on or in production. */
    private void requireCanAssign(Order order, Vendor vendor) {
        requireApproved(order);
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw new ConflictException(vendor.getName() + " is suspended");
        }
        if (!servesCompany(vendor, order)) {
            throw new ConflictException(vendor.getName() + " doesn't serve " + order.getCompany().getName() + " -- map the vendor to the company first");
        }
        if (!servesOrder(vendor, order)) {
            OrgUnit unit = unitOf(order);
            throw new ConflictException(vendor.getName() + " isn't mapped to " + (unit != null ? unit.getName() : "this order's unit")
                    + " or the division above it -- map it under Vendors first");
        }
        if (order.getStatus() == OrderStatus.DISPATCHED || order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("This order is already " + order.getStatus().name().toLowerCase() + " and can't be reassigned");
        }
    }

    /** An order still waiting for its approver (V74) is not the platform's to route yet: it reaches a vendor only on that yes. */
    private static void requireApproved(Order order) {
        if (order.getApprovalStatus() == OrderApprovalStatus.PENDING) {
            throw new ConflictException("ORD-" + order.getOrderNo() + " is still waiting for its approver, so it can't be sent to a vendor yet");
        }
    }

    private void applyAssignment(Order order, Vendor vendor, OrderAssignmentMode mode) {
        UUID previousVendorId = order.getAssignedVendor() != null ? order.getAssignedVendor().getId() : null;
        order.setAssignedVendor(vendor);
        order.setAssignmentMode(mode);
        order.setAssignedAt(Instant.now());
        if (order.getStatus() == OrderStatus.PROCESSING) {
            order.setStatus(OrderStatus.PRODUCTION_QUEUE);
            orderStatusHistoryService.record(order, OrderStatus.PRODUCTION_QUEUE, currentUser());
        }
        // Emailed after commit (VendorEmailListener): the vendor's new order, and a note to the one it was taken from.
        eventPublisher.publishEvent(new VendorOrderAssignedEvent(order.getId(), vendor.getId(), previousVendorId));
    }

    private Order getEntity(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }
}
