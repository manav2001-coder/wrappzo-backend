package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.WarehouseOrderResponse;
import com.wrapzzo.backend.dto.WarehouseRequest;
import com.wrapzzo.backend.dto.WarehouseResponse;
import com.wrapzzo.backend.exception.InvalidOrderStatusTransitionException;
import com.wrapzzo.backend.exception.OrderNotFoundException;
import com.wrapzzo.backend.exception.OrgUnitNotFoundException;
import com.wrapzzo.backend.exception.WarehouseNotFoundException;
import com.wrapzzo.backend.model.CompanyFeature;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Warehouse;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.WarehouseRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The warehouse leg (WAREHOUSE_LOGISTICS): vendor -> company warehouse -> recipient. An order is
 * routed to a warehouse when it is placed; the vendor ships it there (DISPATCHED), and the
 * warehouse moves it on: RECEIVED_AT_WAREHOUSE -> OUT_FOR_DELIVERY -> DELIVERED.
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

    /** The one step a warehouse may take from each stage -- forward only, never skipping one. */
    private static final Map<OrderStatus, OrderStatus> NEXT_STAGE = Map.of(
            OrderStatus.DISPATCHED, OrderStatus.RECEIVED_AT_WAREHOUSE,
            OrderStatus.RECEIVED_AT_WAREHOUSE, OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);

    /** What the warehouse queue shows: inbound from the vendor, on hand, and out for delivery. */
    private static final List<OrderStatus> QUEUE_STATUSES = List.of(
            OrderStatus.PRODUCTION_QUEUE, OrderStatus.DISPATCHED, OrderStatus.RECEIVED_AT_WAREHOUSE,
            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);

    private final WarehouseRepository warehouseRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final OrderRepository orderRepository;
    private final QuickGiftRequestRepository quickGiftRequestRepository;
    private final UserRepository userRepository;
    private final VisibilityService visibilityService;
    private final OrderStatusHistoryService orderStatusHistoryService;
    private final CompanyFeatureFlagService companyFeatureFlagService;

    @Transactional(readOnly = true)
    public List<WarehouseResponse> list() {
        return warehouseRepository.findByCompanyIdOrderByNameAsc(currentUser().getCompany().getId()).stream()
                .map(WarehouseResponse::from)
                .toList();
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        User user = currentUser();
        Warehouse warehouse = new Warehouse(user.getCompany(), request.name().trim(), request.address().trim());
        apply(warehouse, request, user);
        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        User user = currentUser();
        Warehouse warehouse = getOwnCompanyWarehouse(id, user);
        warehouse.setName(request.name().trim());
        warehouse.setAddress(request.address().trim());
        apply(warehouse, request, user);
        return WarehouseResponse.from(warehouse);
    }

    private void apply(Warehouse warehouse, WarehouseRequest request, User user) {
        warehouse.setContactPhone(request.contactPhone() != null && !request.contactPhone().isBlank() ? request.contactPhone().trim() : null);
        warehouse.setOrgUnit(request.orgUnitId() != null ? ownCompanyOrgUnit(request.orgUnitId(), user) : null);
        if (request.active() != null) {
            warehouse.setActive(request.active());
        }
    }

    /**
     * Called as an order is placed. With WAREHOUSE_LOGISTICS on, picks the active warehouse
     * serving the order's org unit most closely -- its own unit, else the nearest ancestor -- and
     * falls back to a company-wide one. No match (or the flag off) leaves the vendor shipping
     * straight to the recipient.
     */
    public void routeOnPlacement(Order order) {
        UUID companyId = order.getCompany().getId();
        if (!companyFeatureFlagService.isEnabled(companyId, CompanyFeature.WAREHOUSE_LOGISTICS)) {
            return;
        }
        List<Warehouse> warehouses = warehouseRepository.findByCompanyIdAndActiveTrue(companyId);
        OrgUnit unit = order.getOrgUnit();
        Warehouse nearest = null;
        if (unit != null) {
            nearest = warehouses.stream()
                    .filter(w -> w.getOrgUnit() != null
                            && (w.getOrgUnit().getId().equals(unit.getId()) || unit.getAncestorIds().contains(w.getOrgUnit().getId())))
                    .max(Comparator.comparingInt(w -> w.getOrgUnit().getAncestorIds().size()))
                    .orElse(null);
        }
        if (nearest == null) {
            nearest = warehouses.stream()
                    .filter(w -> w.getOrgUnit() == null)
                    .min(Comparator.comparing(Warehouse::getName))
                    .orElse(null);
        }
        order.setWarehouse(nearest);
    }

    /** Warehouse-routed orders the caller can see (see Visibility), from production through delivery. */
    @Transactional(readOnly = true)
    public List<WarehouseOrderResponse> listOrders() {
        User user = currentUser();
        Visibility visibility = visibilityService.resolve(user);
        List<Order> orders = orderRepository
                .findByCompanyIdAndWarehouseIsNotNullAndStatusInOrderByUpdatedAtDesc(user.getCompany().getId(), QUEUE_STATUSES)
                .stream()
                .filter(o -> visibility.canSee(o.getOwner(), o.getOrgUnit()))
                .toList();
        Map<UUID, QuickGiftRequest> requestByOrderId = quickGiftRequestRepository
                .findByOrder_IdIn(orders.stream().map(Order::getId).toList())
                .stream()
                .collect(Collectors.toMap(r -> r.getOrder().getId(), Function.identity()));
        return orders.stream().map(o -> WarehouseOrderResponse.from(o, requestByOrderId.get(o.getId()))).toList();
    }

    /** One step along the warehouse leg; anything but the next stage from the current one is a 409. */
    @Transactional
    public WarehouseOrderResponse advance(UUID orderId, OrderStatus newStatus) {
        User user = currentUser();
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getWarehouse() == null
                || !order.getCompany().getId().equals(user.getCompany().getId())
                || !visibilityService.resolve(user).canSee(order.getOwner(), order.getOrgUnit())) {
            throw new OrderNotFoundException(orderId);
        }
        if (NEXT_STAGE.get(order.getStatus()) != newStatus) {
            throw new InvalidOrderStatusTransitionException(orderId, order.getStatus(), newStatus);
        }
        order.setStatus(newStatus);
        orderStatusHistoryService.record(order, newStatus, user);
        return WarehouseOrderResponse.from(order, quickGiftRequestRepository.findByOrderId(orderId).orElse(null));
    }

    private Warehouse getOwnCompanyWarehouse(UUID id, User user) {
        Warehouse warehouse = warehouseRepository.findById(id).orElseThrow(() -> new WarehouseNotFoundException(id));
        if (!warehouse.getCompany().getId().equals(user.getCompany().getId())) {
            throw new WarehouseNotFoundException(id);
        }
        return warehouse;
    }

    private OrgUnit ownCompanyOrgUnit(UUID id, User user) {
        OrgUnit unit = orgUnitRepository.findById(id).orElseThrow(() -> new OrgUnitNotFoundException(id));
        if (!unit.getCompany().getId().equals(user.getCompany().getId())) {
            throw new OrgUnitNotFoundException(id);
        }
        return unit;
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }
}
