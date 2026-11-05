package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.Template;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Company Admin holds every company-assignable key -- that is what the role is. At startup this keeps it true: any key
 * a release adds reaches the system "Company Admin" template and the templates the Company Admin roles use, so a new key
 * no longer needs a hand-written grant in its migration (and cannot be forgotten, as approval.view once was).
 *
 * <p>Only a template used by nothing but Company Admin roles is topped up -- one shared with another role would hand that
 * role everything. Runs after {@link CompanyFeatureRegistrar}; the cache is cleared once the app is ready.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class CompanyAdminAccessReconciler implements ApplicationRunner {

    private final TemplateRepository templateRepository;
    private final RoleRepository roleRepository;
    private final AccessControlRepository accessControlRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int added = reconcile();
        if (added > 0) {
            log.info("Granted {} missing key(s) to the Company Admin templates", added);
        }
    }

    /** Returns how many grants were added. */
    @Transactional
    public int reconcile() {
        List<AccessControl> everyKey = accessControlRepository.findByCompanyAssignableTrue();
        List<Role> roles = roleRepository.findAll();

        // Which roles use which template, so a shared one can be left alone.
        Map<UUID, Set<String>> roleNamesByTemplate = new LinkedHashMap<>();
        Map<UUID, Template> templatesById = new LinkedHashMap<>();
        for (Role role : roles) {
            for (Template template : role.getTemplates()) {
                roleNamesByTemplate.computeIfAbsent(template.getId(), id -> new HashSet<>()).add(role.getName());
                templatesById.put(template.getId(), template);
            }
        }
        templateRepository.findSystemByName(RoleService.COMPANY_ADMIN_ROLE_NAME).ifPresent(template -> templatesById.putIfAbsent(template.getId(), template));

        int added = 0;
        for (Template template : templatesById.values()) {
            Set<String> users = roleNamesByTemplate.getOrDefault(template.getId(), Set.of());
            boolean companyAdminOnly = users.stream().allMatch(RoleService.COMPANY_ADMIN_ROLE_NAME::equals);
            boolean isAdminTemplate = users.contains(RoleService.COMPANY_ADMIN_ROLE_NAME)
                    || (template.isSystem() && RoleService.COMPANY_ADMIN_ROLE_NAME.equals(template.getName()));
            if (!isAdminTemplate || !companyAdminOnly || !template.isActive()) {
                continue;
            }
            for (AccessControl key : everyKey) {
                if (template.getAccessControls().add(key)) {
                    added++;
                }
            }
        }
        return added;
    }
}
