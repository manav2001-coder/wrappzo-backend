package com.wrapzzo.backend.service;

import com.wrapzzo.backend.list.ListFields;
import com.wrapzzo.backend.model.Recipient;
import com.wrapzzo.backend.model.RecipientAssignment;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** What a Doctors list can be searched, filtered and sorted by, and the doctors a user can reach (Visibility). */
final class RecipientListFields {

    private RecipientListFields() {
    }

    static ListFields<Recipient> fields() {
        return new ListFields<Recipient>()
                .text("pcode", (root, cb) -> root.get("pcode"), "pcode", true)
                .text("name", (root, cb) -> root.get("name"), "name", true)
                .text("phone", (root, cb) -> root.get("phone"), "phone", true)
                .text("email", (root, cb) -> root.get("email"), "email", true)
                .text("speciality", (root, cb) -> root.get("speciality"), "speciality", true)
                .text("clinicName", (root, cb) -> root.get("clinicName"), "clinicName", true)
                .text("createdByName", (root, cb) -> ListFields.join(root, "createdBy").get("name"), "createdBy.name", false)
                .day("dateOfBirth", (root, cb) -> root.get("dateOfBirth"), "dateOfBirth")
                .date("createdAt", (root, cb) -> root.get("createdAt"), "createdAt");
    }

    /** Doctors in reach: whoever added them or has them on a list is visible to the caller -- the same rule as RecipientRepository.findVisible. */
    static Specification<Recipient> visibleIn(UUID companyId, Visibility visibility) {
        return (root, query, cb) -> {
            Predicate company = cb.equal(root.get("company").get("id"), companyId);
            if (visibility.companyWide()) {
                return company;
            }
            var creator = ListFields.<Recipient, com.wrapzzo.backend.model.User>join(root, "createdBy");
            Subquery<Integer> assigned = query.subquery(Integer.class);
            var assignment = assigned.from(RecipientAssignment.class);
            assigned.select(cb.literal(1)).where(
                    cb.equal(assignment.get("recipient"), root),
                    cb.or(assignment.get("mrUser").get("id").in(visibility.peopleForQuery()),
                            assignment.get("mrUser").get("orgUnit").get("id").in(visibility.orgUnitsForQuery())));
            return cb.and(company, cb.or(
                    creator.get("id").in(visibility.peopleForQuery()),
                    creator.get("orgUnit").get("id").in(visibility.orgUnitsForQuery()),
                    cb.exists(assigned)));
        };
    }

    /** Only the doctors on this user's own list (recipient_assignments). */
    static Specification<Recipient> onListOf(UUID userId) {
        return (root, query, cb) -> {
            Subquery<Integer> assigned = query.subquery(Integer.class);
            var assignment = assigned.from(RecipientAssignment.class);
            assigned.select(cb.literal(1)).where(cb.equal(assignment.get("recipient"), root), cb.equal(assignment.get("mrUser").get("id"), userId));
            return cb.exists(assigned);
        };
    }
}
