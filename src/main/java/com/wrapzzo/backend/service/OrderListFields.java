package com.wrapzzo.backend.service;

import com.wrapzzo.backend.list.ListFields;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderApprovalStatus;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * What an Orders list can be searched, filtered and sorted by, and the scope a caller is limited to. The vendor is a field only for
 * someone who may know the vendor (VendorIdentityPolicy): leaving it out of the allow-list is what keeps its name from being searched,
 * filtered or sorted by -- and so guessed at -- by anyone else.
 */
final class OrderListFields {

    private OrderListFields() {
    }

    static ListFields<Order> of(boolean showVendor, boolean crossCompany) {
        ListFields<Order> fields = new ListFields<Order>()
                .text("displayId", (root, cb) -> cb.concat("ORD-", root.get("orderNo").as(String.class)), "orderNo", true)
                .text("giftName", (root, cb) -> ListFields.join(root, "gift").get("name"), "gift.name", true)
                .text("recipientName", (root, cb) -> root.get("recipientName"), "recipientName", true)
                .text("recipientPhone", (root, cb) -> root.get("recipientPhone"), "recipientPhone", true)
                .text("orgUnitName", (root, cb) -> ListFields.join(root, "orgUnit").get("name"), "orgUnit.name", true)
                .text("requestedByName", (root, cb) -> ListFields.join(root, "owner").get("name"), "owner.name", true)
                .text("campaignName", (root, cb) -> ListFields.join(root, "campaign").get("name"), "campaign.name", false)
                .number("quantity", (root, cb) -> root.get("quantity"), "quantity")
                .number("totalPrice", (root, cb) -> root.get("totalPrice"), "totalPrice")
                .enumeration("status", OrderStatus.class, (root, cb) -> root.get("status"), "status")
                .enumeration("source", OrderSource.class, (root, cb) -> root.get("source"), "source")
                .enumeration("approvalStatus", OrderApprovalStatus.class, (root, cb) -> root.get("approvalStatus"), "approvalStatus")
                .date("createdAt", (root, cb) -> root.get("createdAt"), "createdAt")
                .flag("hasPendingDesignReview", (root, query, cb) -> {
                    Subquery<Integer> proofs = query.subquery(Integer.class);
                    var proof = proofs.from(OrderDesign.class);
                    proofs.select(cb.literal(1)).where(cb.equal(proof.get("order"), root), cb.equal(proof.get("status"), OrderDesignStatus.PENDING_REVIEW));
                    return cb.exists(proofs);
                })
                .flag("unassigned", (root, query, cb) -> notWithAVendor(root, cb));
        if (showVendor) {
            fields.text("assignedVendorName", (root, cb) -> ListFields.join(root, "assignedVendor").get("name"), "assignedVendor.name", true)
                    .id("assignedVendorId", (root, cb) -> root.get("assignedVendor").get("id"), null);
        }
        if (crossCompany) {
            fields.text("companyName", (root, cb) -> ListFields.join(root, "company").get("name"), "company.name", true)
                    .id("companyId", (root, cb) -> root.get("company").get("id"), null);
        }
        return fields;
    }

    /** Not yet with a vendor and still live: cancelled orders and ones held for their approver are not waiting for one. */
    private static Predicate notWithAVendor(jakarta.persistence.criteria.Root<Order> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.and(
                cb.isNull(root.get("assignedVendor")),
                cb.notEqual(root.get("status"), OrderStatus.CANCELLED),
                cb.notEqual(root.get("approvalStatus"), OrderApprovalStatus.PENDING));
    }

    /** The company's orders a user may see (Visibility): everyone's if company-wide, else their own, their team's and their org units'. */
    static Specification<Order> visibleIn(UUID companyId, Visibility visibility) {
        return (root, query, cb) -> {
            Predicate company = cb.equal(root.get("company").get("id"), companyId);
            if (visibility.companyWide()) {
                return company;
            }
            return cb.and(company, cb.or(
                    root.get("owner").get("id").in(visibility.peopleForQuery()),
                    root.get("orgUnit").get("id").in(visibility.orgUnitsForQuery())));
        };
    }

    static Specification<Order> inCompany(UUID companyId) {
        return (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId);
    }
}
