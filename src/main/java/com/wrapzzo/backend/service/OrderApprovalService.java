package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.PendingOrderApprovalResponse;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.OrderNotFoundException;
import com.wrapzzo.backend.model.ArtworkApprovalScope;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderApprovalStatus;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.security.Permission;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order approval (V74): with it on for a company, a Quick Gift / PEL order waits for its approver -- a role found in the
 * requester's reporting line or division, an RM say -- and only their yes sends it on to the vendor, by the same
 * assignment as before. A no cancels it. The requester never decides their own order. Only when nobody holds the approver
 * role for the orderer may someone with Final Artwork Approval who can see the order decide it as cover, so an order
 * with no approver is never stranded -- and one with an approver is never open to anyone else.
 */
@Service
@RequiredArgsConstructor
public class OrderApprovalService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final QuickGiftRequestRepository quickGiftRequestRepository;
    private final OrderService orderService;
    private final OrderStatusHistoryService orderStatusHistoryService;
    private final AccessControlCacheService accessControlCacheService;
    private final VisibilityService visibilityService;
    private final ApplicationEventPublisher eventPublisher;

    /** Whether this order must wait: approval is on with an approver picked, and the requester is not that approver themselves. */
    public boolean requiresApproval(Order order) {
        Company company = order.getCompany();
        if (!company.isOrderApprovalEnabled() || company.getOrderApprovalRole() == null) {
            return false;
        }
        User requester = order.getOwner();
        return requester == null || requester.getRole() == null || !requester.getRole().getId().equals(company.getOrderApprovalRole().getId());
    }

    /** Holds the order back from every vendor until its approver decides, and tells the approver. */
    @Transactional
    public void submit(Order order) {
        order.setApprovalStatus(OrderApprovalStatus.PENDING);
        eventPublisher.publishEvent(new OrderAwaitingApprovalEvent(order.getId()));
    }

    /** The orders waiting on the caller: the ones they are the approver of, or can cover. */
    @Transactional(readOnly = true)
    public List<PendingOrderApprovalResponse> listPending() {
        User user = currentUser();
        List<Order> waiting = orderRepository.findByCompanyIdAndApprovalStatusOrderByCreatedAtAsc(user.getCompany().getId(), OrderApprovalStatus.PENDING);
        Set<String> keys = accessControlCacheService.resolveEffectiveKeys(user);
        return waiting.stream()
                .filter(order -> mayDecide(order, user, keys))
                .map(order -> PendingOrderApprovalResponse.from(order, quickGiftRequestRepository.findByOrderId(order.getId()).orElse(null)))
                .toList();
    }

    @Transactional
    public PendingOrderApprovalResponse approve(UUID orderId, String comment) {
        Order order = decidable(orderId);
        order.setApprovalStatus(OrderApprovalStatus.APPROVED);
        stamp(order, comment);
        // The same hand-off as an order raised without approval: to the vendor of the gift that was picked.
        orderService.assignOnPlacement(order, order.getGift().getVendor());
        eventPublisher.publishEvent(new OrderApprovalDecidedEvent(order.getId()));
        return PendingOrderApprovalResponse.from(order, quickGiftRequestRepository.findByOrderId(order.getId()).orElse(null));
    }

    @Transactional
    public PendingOrderApprovalResponse reject(UUID orderId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new ConflictException("Say why you are not approving this order -- the person who placed it will see your note.");
        }
        Order order = decidable(orderId);
        order.setApprovalStatus(OrderApprovalStatus.REJECTED);
        stamp(order, comment);
        order.setStatus(OrderStatus.CANCELLED);
        orderStatusHistoryService.record(order, OrderStatus.CANCELLED, currentUser());
        eventPublisher.publishEvent(new OrderApprovalDecidedEvent(order.getId()));
        return PendingOrderApprovalResponse.from(order, quickGiftRequestRepository.findByOrderId(order.getId()).orElse(null));
    }

    /** Who an order waits on and may be emailed: the people at its approver stage who are active and hold approval.decide. */
    @Transactional(readOnly = true)
    public List<User> awaitedApprovers(Order order) {
        return approverStage(order).eligible().stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE
                        && accessControlCacheService.resolveEffectiveKeys(user).contains(Permission.APPROVAL_DECIDE.key()))
                .toList();
    }

    private Order decidable(UUID orderId) {
        User user = currentUser();
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getCompany().getId().equals(user.getCompany().getId())) {
            throw new OrderNotFoundException(orderId);
        }
        if (order.getApprovalStatus() != OrderApprovalStatus.PENDING) {
            throw new ConflictException("ORD-" + order.getOrderNo() + " is not waiting for approval"
                    + (order.getApprovalStatus() == OrderApprovalStatus.NOT_REQUIRED ? " -- it did not need any." : " -- it has already been decided."));
        }
        if (!mayDecide(order, user, accessControlCacheService.resolveEffectiveKeys(user))) {
            throw new ConflictException("ORD-" + order.getOrderNo() + " is not waiting on you. It waits on " + approverStage(order).label() + ".");
        }
        return order;
    }

    private void stamp(Order order, String comment) {
        order.setApprovalDecidedBy(currentUser());
        order.setApprovalDecidedAt(Instant.now());
        order.setApprovalComment(comment == null || comment.isBlank() ? null : comment.trim());
    }

    /** The approver at the approver stage holding approval.decide, or cover from Final Artwork Approval for an order the caller can see. Never the requester. */
    private boolean mayDecide(Order order, User user, Set<String> keys) {
        if (order.getOwner() != null && order.getOwner().getId().equals(user.getId())) {
            return false;
        }
        if (keys.contains(Permission.APPROVAL_DECIDE.key()) && approverStage(order).includes(user)) {
            return true;
        }
        // Cover only where nobody can decide it -- no one holds the approver role for this orderer. An order that has its
        // approver is that person's alone: an HO, say, who holds Final Artwork Approval, does not see or decide it.
        return keys.contains(Permission.ARTWORK_FINAL_APPROVE.key())
                && awaitedApprovers(order).isEmpty()
                && visibilityService.resolve(user).canSee(order.getOwner(), order.getOrgUnit());
    }

    /** One approver stage: who may decide, and how to name them (a person, or "RM of Summit"). */
    private record Stage(User named, List<User> eligible, String label) {
        boolean includes(User user) {
            return eligible.stream().anyMatch(candidate -> candidate.getId().equals(user.getId()));
        }
    }

    /** Resolved fresh each time, so a change in the reporting line, a placement or the company's setup applies straight away. */
    private Stage approverStage(Order order) {
        Company company = order.getCompany();
        User requester = order.getOwner();
        if (company.getOrderApprovalRole() == null || requester == null) {
            return new Stage(null, List.of(), "its approver");
        }
        UUID roleId = company.getOrderApprovalRole().getId();
        String roleName = company.getOrderApprovalRole().getName();
        if (company.getOrderApprovalScope() == ArtworkApprovalScope.REPORTING_LINE) {
            Set<UUID> seen = new HashSet<>(List.of(requester.getId()));
            for (User manager = requester.getReportsTo(); manager != null && seen.add(manager.getId()); manager = manager.getReportsTo()) {
                if (manager.getRole() != null && manager.getRole().getId().equals(roleId)) {
                    return new Stage(manager, List.of(manager), manager.getName());
                }
            }
            return new Stage(null, List.of(), roleName + " (none above " + requester.getName() + ")");
        }
        OrgUnit division = divisionOf(requester);
        List<User> holders = division == null
                ? List.of()
                : userRepository.findByCompanyIdAndRoleId(company.getId(), roleId).stream()
                        .filter(user -> user.getStatus() == UserStatus.ACTIVE && coversDivision(user, division))
                        .toList();
        return new Stage(holders.size() == 1 ? holders.get(0) : null, holders, roleName + (division != null ? " of " + division.getName() : ""));
    }

    private OrgUnit divisionOf(User user) {
        OrgUnit unit = user.getOrgUnit();
        if (unit == null) {
            return null;
        }
        return unit.getAncestorIds().isEmpty() ? unit : orgUnitRepository.findById(unit.getAncestorIds().get(0)).orElse(unit);
    }

    private static boolean coversDivision(User user, OrgUnit division) {
        return (user.getOrgUnit() != null && user.getOrgUnit().getId().equals(division.getId()))
                || user.getExtraOrgUnits().stream().anyMatch(unit -> unit.getId().equals(division.getId()));
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }
}
