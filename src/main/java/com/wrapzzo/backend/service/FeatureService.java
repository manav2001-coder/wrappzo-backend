package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.FeatureResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.InvalidTemplateException;
import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyAccessControl;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyAccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The feature master, per company. The permission keys live in code (security.Permission, checked
 * against the database at startup), so a company's master lists the catalogue with what it has
 * registered and switched on -- registering adds a key to the company, never invents one.
 * Registering only ever adds, so a feature an admin switched off stays off across restarts.
 */
@Service
@RequiredArgsConstructor
public class FeatureService {

    /** Modules that run the access screens themselves: switching one off would lock the company out of repairing access. */
    private static final Set<String> ALWAYS_ON_MODULES = Set.of("role", "template", "access");

    private final AccessControlRepository accessControlRepository;
    private final CompanyAccessControlRepository companyAccessControlRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CompanyAccessGuard companyAccessGuard;
    private final AccessControlCacheService accessControlCacheService;

    /** Every key a company could hold, with whether this company has it and whether it is on. */
    @Transactional(readOnly = true)
    public List<FeatureResponse> listForCompany(UUID companyId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Map<UUID, CompanyAccessControl> registered = companyAccessControlRepository.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(row -> row.getAccessControl().getId(), row -> row));
        return accessControlRepository.findByCompanyAssignableTrue().stream()
                .sorted(Comparator.comparingInt(AccessControl::getSortOrder))
                .map(feature -> {
                    CompanyAccessControl row = registered.get(feature.getId());
                    return FeatureResponse.from(feature, row != null, row != null && row.isActive());
                })
                .toList();
    }

    /** The whole catalogue: platform staff see every key (for system templates); a company, the keys it can hold. */
    @Transactional(readOnly = true)
    public List<FeatureResponse> catalogue() {
        boolean platformStaff = caller().getCompany() == null;
        return accessControlRepository.findAll().stream()
                .filter(feature -> platformStaff || feature.isCompanyAssignable())
                .sorted(Comparator.comparingInt(AccessControl::getSortOrder))
                .map(feature -> FeatureResponse.from(feature, true, true))
                .toList();
    }

    /** Registers features for a company; with no ids, every company-assignable feature it is missing. */
    @Transactional
    public List<FeatureResponse> register(UUID companyId, Set<UUID> featureIds) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
        List<AccessControl> wanted = featureIds == null
                ? accessControlRepository.findByCompanyAssignableTrue()
                : accessControlRepository.findAllById(featureIds);
        if (featureIds != null && wanted.size() != featureIds.size()) {
            throw new InvalidTemplateException("One or more selected features don't exist.");
        }
        List<String> platformOnly = wanted.stream().filter(feature -> !feature.isCompanyAssignable()).map(AccessControl::getKey).toList();
        if (!platformOnly.isEmpty()) {
            throw new InvalidTemplateException("These features are platform-only and can't be registered for a company: " + String.join(", ", platformOnly));
        }
        addMissing(company, wanted);
        accessControlCacheService.invalidateAfterCommit();
        return listForCompany(companyId);
    }

    @Transactional
    public FeatureResponse setActive(UUID companyId, UUID featureId, boolean active) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        CompanyAccessControl row = companyAccessControlRepository.findByCompanyIdAndAccessControlId(companyId, featureId)
                .orElseThrow(() -> new InvalidTemplateException("That feature isn't registered for this company -- add it first."));
        AccessControl feature = row.getAccessControl();
        if (!active && ALWAYS_ON_MODULES.contains(feature.getModule())) {
            throw new ConflictException("\"" + feature.getCategory() + "\" runs the access screens and can't be switched off");
        }
        row.setActive(active);
        accessControlCacheService.invalidateAfterCommit();
        return FeatureResponse.from(feature, true, active);
    }

    /** A new company starts with every feature it can hold. */
    @Transactional
    public void registerAll(Company company) {
        addMissing(company, accessControlRepository.findByCompanyAssignableTrue());
    }

    /** Boot-time catch-up (CompanyFeatureRegistrar): a key added in a release reaches companies that predate it. */
    @Transactional
    public int registerMissingForAllCompanies() {
        List<AccessControl> catalogue = accessControlRepository.findByCompanyAssignableTrue();
        int added = 0;
        for (Company company : companyRepository.findAll()) {
            added += addMissing(company, catalogue);
        }
        if (added > 0) {
            accessControlCacheService.invalidateAfterCommit();
        }
        return added;
    }

    private int addMissing(Company company, List<AccessControl> features) {
        Set<UUID> have = new HashSet<>(
                companyAccessControlRepository.findByCompanyId(company.getId()).stream().map(row -> row.getAccessControl().getId()).toList());
        int added = 0;
        for (AccessControl feature : features) {
            if (have.add(feature.getId())) {
                companyAccessControlRepository.save(new CompanyAccessControl(company, feature));
                added++;
            }
        }
        return added;
    }

    private User caller() {
        UUID callerId = CurrentUser.id();
        return userRepository.findById(callerId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + callerId));
    }
}
