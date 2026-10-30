package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.DesignationAccessResponse;
import com.wrapzzo.backend.dto.DesignationAccessResponse.RoleGrant;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.InvalidAccessControlSelectionException;
import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.DesignationAccessControl;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.security.Permission;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.DesignationAccessControlRepository;
import com.wrapzzo.backend.repository.RoleAccessControlRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final RoleAccessControlRepository roleAccessControlRepository;

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
        return new DesignationAccessResponse(designation, accessControlIds, roleGrants(companyId, designation));
    }

    /** The roles held by this designation's users, each with its own keys -- most of what they can do comes from here. */
    private List<RoleGrant> roleGrants(UUID companyId, String designation) {
        Map<Role, Long> usersByRole = userRepository.findByCompanyIdAndDesignation(companyId, designation).stream()
                .filter(user -> user.getRole() != null)
                .collect(Collectors.groupingBy(User::getRole, Collectors.counting()));
        return usersByRole.entrySet().stream()
                .map(entry -> new RoleGrant(
                        entry.getKey().getName(),
                        entry.getValue().intValue(),
                        roleAccessControlRepository.findByRoleId(entry.getKey().getId()).stream()
                                .map(grant -> grant.getAccessControl().getId())
                                .collect(Collectors.toSet())))
                .sorted(Comparator.comparing(RoleGrant::userCount).reversed().thenComparing(RoleGrant::roleName))
                .toList();
    }

    @Transactional
    public DesignationAccessResponse replaceAccessControls(UUID companyId, String designation, Set<UUID> accessControlIds) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));

        List<AccessControl> accessControls = withImpliedViews(accessControlRepository.findAllById(accessControlIds));
        if (accessControls.stream().map(AccessControl::getId).filter(accessControlIds::contains).count() != accessControlIds.size()) {
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
        Set<UUID> granted = accessControls.stream().map(AccessControl::getId).collect(Collectors.toSet());
        return new DesignationAccessResponse(designation, granted, roleGrants(companyId, designation));
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

    /**
     * Any action of a module brings its view with it ("Edit campaigns" without "View campaigns"
     * isn't a usable grant) -- applied here, not only in the access screen, so every grant path agrees.
     */
    private List<AccessControl> withImpliedViews(List<AccessControl> selected) {
        Set<String> keys = selected.stream().map(AccessControl::getKey).collect(Collectors.toSet());
        Set<String> missingViews = selected.stream()
                .map(ac -> Permission.fromKey(ac.getKey()).flatMap(Permission::impliedView))
                .flatMap(Optional::stream)
                .map(Permission::key)
                .filter(key -> !keys.contains(key))
                .collect(Collectors.toSet());
        if (missingViews.isEmpty()) {
            return selected;
        }
        List<AccessControl> all = new ArrayList<>(selected);
        missingViews.forEach(key -> accessControlRepository.findByKey(key).ifPresent(all::add));
        return all;
    }
}
