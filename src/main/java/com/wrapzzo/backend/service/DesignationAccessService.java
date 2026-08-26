package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.DesignationAccessResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.InvalidAccessControlSelectionException;
import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.DesignationAccessControl;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.DesignationAccessControlRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company-scoped access grants keyed by a designation (the free-text users.designation value)
 * instead of a Role -- independent of and additive to the Role/role_access_controls system
 * (see AccessControlCacheService.resolveEffectiveKeys, which unions the two).
 */
@Service
@RequiredArgsConstructor
public class DesignationAccessService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final DesignationAccessControlRepository designationAccessControlRepository;
    private final AccessControlRepository accessControlRepository;
    private final CompanyAccessGuard companyAccessGuard;
    private final AccessControlCacheService accessControlCacheService;

    @Transactional(readOnly = true)
    public List<String> listDesignations(UUID companyId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        return userRepository.findDistinctDesignationsByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public DesignationAccessResponse getAccessControls(UUID companyId, String designation) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Set<UUID> accessControlIds =
                new HashSet<>(designationAccessControlRepository.findAccessControlIdsByCompanyIdAndDesignation(companyId, designation));
        return new DesignationAccessResponse(designation, accessControlIds);
    }

    @Transactional
    public DesignationAccessResponse replaceAccessControls(UUID companyId, String designation, Set<UUID> accessControlIds) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));

        List<AccessControl> accessControls = accessControlRepository.findAllById(accessControlIds);
        if (accessControls.size() != accessControlIds.size()) {
            throw new InvalidAccessControlSelectionException();
        }
        List<String> platformOnlyKeys =
                accessControls.stream().filter(ac -> !ac.isCompanyAssignable()).map(AccessControl::getKey).toList();
        if (!platformOnlyKeys.isEmpty()) {
            throw new InvalidAccessControlSelectionException(
                    "These keys are platform-only and can't be granted to a company designation: " + String.join(", ", platformOnlyKeys));
        }

        designationAccessControlRepository.deleteByCompanyIdAndDesignation(companyId, designation);
        for (AccessControl accessControl : accessControls) {
            designationAccessControlRepository.save(new DesignationAccessControl(company, designation, accessControl));
        }
        accessControlCacheService.invalidateDesignationPermissions(companyId, designation);
        return new DesignationAccessResponse(designation, accessControlIds);
    }

    /**
     * Additive union onto whatever the designation already grants -- never removes an existing
     * grant, unlike replaceAccessControls. Used to auto-propagate Company Admin's access onto a
     * promoted user's designation (see UserService.makeCompanyAdmin), so anyone else sharing
     * that same designation -- now or hired later -- picks it up too, without Super Admin
     * having to hand-configure that designation separately.
     */
    @Transactional
    public void grantAccessControls(UUID companyId, String designation, Set<UUID> accessControlIdsToAdd) {
        if (designation == null || accessControlIdsToAdd.isEmpty()) {
            return;
        }
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));

        Set<UUID> existing =
                new HashSet<>(designationAccessControlRepository.findAccessControlIdsByCompanyIdAndDesignation(companyId, designation));
        Set<UUID> newIds = new HashSet<>(accessControlIdsToAdd);
        newIds.removeAll(existing);
        if (newIds.isEmpty()) {
            return;
        }

        // Silently drop platform-only keys rather than throw -- this is an automatic system
        // propagation, not user input, and Company Admin's own grants never include them anyway.
        List<AccessControl> accessControls =
                accessControlRepository.findAllById(newIds).stream().filter(AccessControl::isCompanyAssignable).toList();
        if (accessControls.isEmpty()) {
            return;
        }
        for (AccessControl accessControl : accessControls) {
            designationAccessControlRepository.save(new DesignationAccessControl(company, designation, accessControl));
        }
        accessControlCacheService.invalidateDesignationPermissions(companyId, designation);
    }
}
