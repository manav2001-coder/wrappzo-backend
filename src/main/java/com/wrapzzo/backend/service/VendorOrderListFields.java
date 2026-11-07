package com.wrapzzo.backend.service;

import com.wrapzzo.backend.list.ListFields;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.model.OrderStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * What a vendor's Orders list can be searched, filtered and sorted by, and the scope (their own orders). The {@code stage} is the vendor's
 * reading of where an order stands -- what it needs from them next -- worked out in the database by the same rules the portal has always
 * used (see lib/vendor-order-stage.ts); {@code divisionId} narrows to one of the customer's divisions.
 */
final class VendorOrderListFields {

    /** The stages, in the vendor's queue order. Their names are what the portal sends and shows. */
    static final List<String> STAGES = List.of("rework", "artwork", "review", "ready", "dispatched", "warehouse", "delivered", "cancelled");

    private static final List<OrderStatus> OPEN = List.of(OrderStatus.PROCESSING, OrderStatus.PRODUCTION_QUEUE);

    private VendorOrderListFields() {
    }

    static ListFields<Order> fields() {
        return new ListFields<Order>()
                .text("displayId", (root, cb) -> cb.concat("ORD-", root.get("orderNo").as(String.class)), "orderNo", true)
                .text("giftName", (root, cb) -> ListFields.join(root, "gift").get("name"), "gift.name", true)
                .text("companyName", (root, cb) -> ListFields.join(root, "company").get("name"), "company.name", true)
                .text("recipientName", (root, cb) -> root.get("recipientName"), "recipientName", true)
                .number("quantity", (root, cb) -> root.get("quantity"), "quantity")
                .date("assignedAt", (root, cb) -> root.get("assignedAt"), "assignedAt")
                .date("createdAt", (root, cb) -> root.get("createdAt"), "createdAt")
                .id("divisionId", (root, cb) -> root.get("divisionId"), "divisionId")
                .choice("stage", VendorOrderListFields::stage);
    }

    static Specification<Order> ofVendor(UUID vendorId) {
        return (root, query, cb) -> cb.equal(root.get("assignedVendor").get("id"), vendorId);
    }

    static Specification<Order> inDivision(UUID divisionId) {
        return (root, query, cb) -> cb.equal(root.get("divisionId"), divisionId);
    }

    static Specification<Order> atStage(String stage) {
        return (root, query, cb) -> {
            Predicate holds = stage(stage, root, query, cb);
            return holds == null ? cb.disjunction() : holds;
        };
    }

    /** The predicate for one stage, or null if there is no such stage. Mirrors vendorStageOf(): status first, then the artwork loop for an order still with the vendor. */
    static Predicate stage(String stage, Root<Order> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        var status = root.<OrderStatus>get("status");
        Predicate withWarehouse = cb.isNotNull(root.get("warehouse"));
        Predicate open = status.in(OPEN);
        return switch (stage) {
            case "cancelled" -> cb.equal(status, OrderStatus.CANCELLED);
            case "delivered" -> cb.equal(status, OrderStatus.DELIVERED);
            case "warehouse" -> cb.or(
                    status.in(List.of(OrderStatus.RECEIVED_AT_WAREHOUSE, OrderStatus.OUT_FOR_DELIVERY)),
                    cb.and(cb.equal(status, OrderStatus.DISPATCHED), withWarehouse));
            case "dispatched" -> cb.and(cb.equal(status, OrderStatus.DISPATCHED), cb.not(withWarehouse));
            case "review" -> cb.and(open, needsArtwork(root, query, cb),
                    latestProofIs(List.of(OrderDesignStatus.PENDING_REVIEW, OrderDesignStatus.PENDING_FINAL_APPROVAL), root, query, cb));
            case "rework" -> cb.and(open, needsArtwork(root, query, cb), latestProofIs(List.of(OrderDesignStatus.REJECTED), root, query, cb));
            case "artwork" -> cb.and(open, needsArtwork(root, query, cb), cb.not(latestProofIs(
                    List.of(OrderDesignStatus.PENDING_REVIEW, OrderDesignStatus.PENDING_FINAL_APPROVAL, OrderDesignStatus.REJECTED), root, query, cb)));
            case "ready" -> cb.and(open, cb.not(needsArtwork(root, query, cb)));
            default -> null;
        };
    }

    /** The gift needs an approved proof and none is approved yet. */
    private static Predicate needsArtwork(Root<Order> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> approved = query.subquery(Integer.class);
        Root<OrderDesign> design = approved.from(OrderDesign.class);
        approved.select(cb.literal(1)).where(cb.equal(design.get("order"), root), cb.equal(design.get("status"), OrderDesignStatus.APPROVED));
        return cb.and(cb.isTrue(ListFields.join(root, "gift").get("artworkRequired")), cb.not(cb.exists(approved)));
    }

    /** The order's newest proof (highest version) is in one of these states. False when it has no proof yet. */
    private static Predicate latestProofIs(List<OrderDesignStatus> statuses, Root<Order> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> latest = query.subquery(Integer.class);
        Root<OrderDesign> design = latest.from(OrderDesign.class);
        Subquery<Integer> newest = latest.subquery(Integer.class);
        Root<OrderDesign> other = newest.from(OrderDesign.class);
        newest.select(cb.max(other.<Integer>get("versionNo"))).where(cb.equal(other.get("order"), root));
        latest.select(cb.literal(1)).where(cb.equal(design.get("order"), root), cb.equal(design.get("versionNo"), newest), design.get("status").in(statuses));
        return cb.exists(latest);
    }
}
