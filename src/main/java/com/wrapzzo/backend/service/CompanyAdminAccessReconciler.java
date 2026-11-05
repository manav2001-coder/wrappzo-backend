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
 * The Company Admin holds the company's working keys. At startup this keeps it true: any key a release adds reaches the
 * system "Company Admin" template and the templates the Company Admin roles use, so a new key no longer needs a
 * hand-written grant in its migration (and cannot be forgotten, as approval.view once was).
 *
 * <p>Not everything: the access-control screens (templates, features, role and user templates) and the campaign keys are
 * Wrappzo staff's to hand out, and the Company Admin template deliberately lacks them -- {@link #NOT_FOR_COMPANY_ADMIN}.
 * The system "Company Admin" template is always topped up (it is the admin template, whatever a company calls the role
 * that uses it -- "Company Head", say); a company's own template only when nothing but Company Admin roles use it, since
 * one shared with another role would hand that role everything. Runs after {@link CompanyFeatureRegistrar}; the cache
 * is cleared once the app is ready.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class CompanyAdminAccessReconciler implements ApplicationRunner {

    /** Modules whose keys the Company Admin does not hold by default: access control is Wrappzo's, campaigns are being retired. */
    static final Set<String> NOT_FOR_COMPANY_ADMIN = Set.of(
            "feature_master", "template_master", "features_to_template", "templates_to_template", "user_template", "role_template",
            "campaign", "campaign_entry");

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
        List<AccessControl> everyKey = accessControlRepository.findByCompanyAssignableTrue().stream()
                .filter(key -> !NOT_FOR_COMPANY_ADMIN.contains(key.getKey().substring(0, key.getKey().indexOf('.'))))
                .toList();
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
            boolean systemAdminTemplate = template.isSystem() && RoleService.COMPANY_ADMIN_ROLE_NAME.equals(template.getName());
            boolean companyAdminOnly = !users.isEmpty() && users.stream().allMatch(RoleService.COMPANY_ADMIN_ROLE_NAME::equals);
            if (!(systemAdminTemplate || companyAdminOnly) || !template.isActive()) {
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
