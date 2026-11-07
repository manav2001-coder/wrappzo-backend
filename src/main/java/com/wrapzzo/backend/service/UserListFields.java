package com.wrapzzo.backend.service;

import com.wrapzzo.backend.list.ListFields;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** What a Users list can be searched, filtered and sorted by, and the people a caller looks after (Visibility, never themselves). */
final class UserListFields {

    private UserListFields() {
    }

    static ListFields<User> fields() {
        return new ListFields<User>()
                .text("name", (root, cb) -> root.get("name"), "name", true)
                .text("email", (root, cb) -> root.get("email"), "email", true)
                .text("mobile", (root, cb) -> root.get("mobile"), "mobile", true)
                .text("designation", (root, cb) -> root.get("designation"), "designation", true)
                .text("employeeCode", (root, cb) -> root.get("employeeCode"), "employeeCode", true)
                .text("roleName", (root, cb) -> ListFields.join(root, "role").get("name"), "role.name", true)
                .text("orgUnitName", (root, cb) -> ListFields.join(root, "orgUnit").get("name"), "orgUnit.name", true)
                .text("reportsToName", (root, cb) -> ListFields.join(root, "reportsTo").get("name"), "reportsTo.name", true)
                .id("roleId", (root, cb) -> root.get("role").get("id"), null)
                .id("orgUnitId", (root, cb) -> root.get("orgUnit").get("id"), null)
                .enumeration("status", UserStatus.class, (root, cb) -> root.get("status"), "status")
                .bool("companyWide", (root, cb) -> root.get("companyWide"), "companyWide")
                .date("createdAt", (root, cb) -> root.get("createdAt"), "createdAt")
                // The audit columns the screens show, under the names they use.
                .date("addedOn", (root, cb) -> root.get("createdAt"), "createdAt")
                .text("addedByName", (root, cb) -> root.get("createdByName"), "createdByName", false)
                .date("updatedOn", (root, cb) -> root.get("updatedAt"), "updatedAt")
                .text("updatedByName", (root, cb) -> root.get("updatedByName"), "updatedByName", false)
                // The Status column of the shared list screens: an active account.
                .bool("active", (root, cb) -> cb.equal(root.get("status"), UserStatus.ACTIVE), null);
    }

    /** The company's people the caller can see, other than the caller -- the same rule the Users screens have always applied. */
    static Specification<User> lookedAfterBy(UUID companyId, Visibility visibility, UUID callerId) {
        return (root, query, cb) -> {
            Predicate company = cb.and(cb.equal(root.get("company").get("id"), companyId), cb.notEqual(root.get("id"), callerId));
            if (visibility.companyWide()) {
                return company;
            }
            return cb.and(company, cb.or(
                    root.get("id").in(visibility.peopleForQuery()),
                    root.get("orgUnit").get("id").in(visibility.orgUnitsForQuery())));
        };
    }
}
