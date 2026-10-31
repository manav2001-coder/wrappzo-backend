package com.wrapzzo.backend.model;

import com.wrapzzo.backend.repository.UserRepository;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Stamps {@link AuditNames} with the signed-in user's name. Nothing is stamped when no user is
 * signed in (seeding, scheduled jobs) or the caller is a vendor, so those writes keep whatever
 * name the row already had. The lookup is by primary key, so it never flushes mid-callback.
 */
@Component
@RequiredArgsConstructor
public class AuditNameListener {

    private final ObjectProvider<UserRepository> users;

    @PrePersist
    void onCreate(AuditNames row) {
        String actor = actorName();
        if (actor == null) return;
        row.setCreatedByName(actor);
        row.setUpdatedByName(actor);
    }

    @PreUpdate
    void onUpdate(AuditNames row) {
        String actor = actorName();
        if (actor != null) row.setUpdatedByName(actor);
    }

    private String actorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID id)) return null;
        return users.getObject().findById(id).map(User::getName).orElse(null);
    }
}
