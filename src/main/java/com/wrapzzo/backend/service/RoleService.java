package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.RoleRequest;
import com.wrapzzo.backend.dto.RoleResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.RoleNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The roles master: a company's roles are created here first, then users are given one, then the
 * role is mapped to templates (TemplateService) -- the role itself grants nothing. Company Admin
 * exists from the start and is protected, since the app finds it by name; every other role is
 * the company's own.
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    public static final String COMPANY_ADMIN_ROLE_NAME = "Company Admin";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyAccessGuard companyAccessGuard;
    private final AccessControlCacheService accessControlCacheService;

    @Transactional(readOnly = true)
    public List<RoleResponse> list(UUID companyId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        return roleRepository.findByCompanyId(companyId).stream()
                .sorted(Comparator.comparing(Role::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    /** A role of the caller's company (platform staff: any), with its users and templates. */
    @Transactional(readOnly = true)
    public RoleResponse get(UUID roleId) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RoleNotFoundException(roleId));
        if (role.getCompany() == null) {
            throw new RoleNotFoundException(roleId);
        }
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(role.getCompany().getId());
        return toResponse(role);
    }

    @Transactional
    public RoleResponse create(UUID companyId, RoleRequest request) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
        String name = request.name().trim();
        requireNameFree(companyId, name, null);
        Role role = new Role(company, name);
        role.setDescription(blankToNull(request.description()));
        roleRepository.save(role);
        accessControlCacheService.invalidateAfterCommit(companyId);
        return toResponse(role);
    }

    @Transactional
    public RoleResponse update(UUID companyId, UUID roleId, RoleRequest request) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Role role = getEntity(companyId, roleId);
        String name = request.name().trim();
        boolean active = request.active() == null || request.active();
        if (isProtected(role) && (!name.equals(role.getName()) || !active)) {
            throw new ConflictException("\"" + role.getName() + "\" can't be renamed or deactivated");
        }
        if (!active && userRepository.countByCompanyIdAndRoleId(companyId, roleId) > 0) {
            throw new ConflictException("Move this role's users to another role before deactivating it");
        }
        requireNameFree(companyId, name, roleId);
        role.setName(name);
        role.setDescription(blankToNull(request.description()));
        role.setActive(active);
        accessControlCacheService.invalidateAfterCommit(companyId);
        return toResponse(role);
    }

    @Transactional
    public void delete(UUID companyId, UUID roleId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Role role = getEntity(companyId, roleId);
        if (isProtected(role)) {
            throw new ConflictException("\"" + role.getName() + "\" can't be removed");
        }
        if (userRepository.countByCompanyIdAndRoleId(companyId, roleId) > 0) {
            throw new ConflictException("Move this role's users to another role before removing it");
        }
        roleRepository.delete(role);
        accessControlCacheService.invalidateAfterCommit(companyId);
    }

    /** A role of the company, or 404 -- another company's role id is never confirmed to exist. */
    Role getEntity(UUID companyId, UUID roleId) {
        return roleRepository.findById(roleId)
                .filter(role -> role.getCompany() != null && role.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RoleNotFoundException(roleId));
    }

    private void requireNameFree(UUID companyId, String name, UUID selfId) {
        roleRepository.findByCompanyIdAndName(companyId, name).filter(existing -> !existing.getId().equals(selfId)).ifPresent(existing -> {
            throw new ConflictException("A role named \"" + name + "\" already exists");
        });
    }

    private static boolean isProtected(Role role) {
        return COMPANY_ADMIN_ROLE_NAME.equals(role.getName());
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.from(role, isProtected(role), userRepository.countByCompanyIdAndRoleId(role.getCompany().getId(), role.getId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
