package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.FeatureResponse;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.InvalidTemplateException;
import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The feature master. The permission keys themselves live in code (security.Permission, checked
 * against the database at startup), so this screen browses them and switches them on or off --
 * it never adds one. Switching is global, so it is platform staff only; a company sees the keys
 * it can hold.
 */
@Service
@RequiredArgsConstructor
public class FeatureService {

    /** Modules that run the access screens themselves: switching one off would lock the company out of repairing access. */
    private static final Set<String> ALWAYS_ON_MODULES = Set.of("role", "template", "access");

    private final AccessControlRepository accessControlRepository;
    private final UserRepository userRepository;
    private final AccessControlCacheService accessControlCacheService;

    @Transactional(readOnly = true)
    public List<FeatureResponse> list() {
        boolean platformStaff = caller().getCompany() == null;
        return accessControlRepository.findAll().stream()
                .filter(accessControl -> platformStaff || accessControl.isCompanyAssignable())
                .sorted(Comparator.comparingInt(AccessControl::getSortOrder))
                .map(FeatureResponse::from)
                .toList();
    }

    @Transactional
    public FeatureResponse setActive(UUID id, boolean active) {
        if (caller().getCompany() != null) {
            throw new AccessDeniedException("Features can only be switched by platform staff");
        }
        AccessControl feature = accessControlRepository.findById(id).orElseThrow(() -> new InvalidTemplateException("Feature not found: " + id));
        if (!active && ALWAYS_ON_MODULES.contains(feature.getModule())) {
            throw new ConflictException("\"" + feature.getCategory() + "\" runs the access screens and can't be switched off");
        }
        feature.setActive(active);
        accessControlCacheService.invalidateAfterCommit();
        return FeatureResponse.from(feature);
    }

    private User caller() {
        UUID callerId = CurrentUser.id();
        return userRepository.findById(callerId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + callerId));
    }
}
