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

    /** The columns people may search, filter and sort by. The activity ones exist only for someone who may see when people were last active. */
    static ListFields<User> fields(boolean withActivity) {
        ListFields<User> fields = baseFields();
        if (withActivity) {
            // Filtered by presets ("not active for a week") and sorted by the time itself.
            fields.choice("activity", UserListFields::activity, "lastActiveAt");
        }
        return fields;
    }

    /**
     * Activity presets for the filter: "today" (the last 24 hours), "week" (the last 7 days), "inactive7" and "inactive30"
     * (signed in before, but not in that long) and "never" (never signed in). Null for anything else.
     */
    static Predicate activity(String value, jakarta.persistence.criteria.Root<User> root, jakarta.persistence.criteria.CriteriaQuery<?> query, jakarta.persistence.criteria.CriteriaBuilder cb) {
        var at = root.<java.time.Instant>get("lastActiveAt");
        var now = java.time.Instant.now();
        return switch (value) {
            case "today" -> cb.greaterThanOrEqualTo(at, now.minus(java.time.Duration.ofHours(24)));
            case "week" -> cb.greaterThanOrEqualTo(at, now.minus(java.time.Duration.ofDays(7)));
            case "inactive7" -> cb.lessThan(at, now.minus(java.time.Duration.ofDays(7)));
            case "inactive30" -> cb.lessThan(at, now.minus(java.time.Duration.ofDays(30)));
            case "never" -> cb.isNull(at);
            default -> null;
        };
    }

    private static ListFields<User> baseFields() {
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
