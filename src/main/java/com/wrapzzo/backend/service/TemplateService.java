package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.AddFeaturesResponse;
import com.wrapzzo.backend.dto.AddReferencesResponse;
import com.wrapzzo.backend.dto.BulkAssignUserTemplatesResponse;
import com.wrapzzo.backend.dto.EffectiveFeaturesResponse;
import com.wrapzzo.backend.dto.TbacTemplateResponse;
import com.wrapzzo.backend.dto.TemplateWriteRequest;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.InvalidTemplateException;
import com.wrapzzo.backend.exception.RoleNotFoundException;
import com.wrapzzo.backend.exception.TemplateNotFoundException;
import com.wrapzzo.backend.exception.UserNotFoundException;
import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.Template;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyAccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access templates -- the only thing that grants. A template is a named bundle of features that can
 * include other templates; roles and users hold templates; nothing holds a bare permission.
 *
 * <p>System templates (no company) are shared by every company and changed by platform staff only;
 * a company builds its own. The company an operation applies to comes from the caller's account;
 * platform staff, who belong to none, name it on the request (and name none for the system set).
 * Rules enforced here, not in the UI: a company template carries only features the company has
 * registered and switched on; it includes only system templates or its own company's; it never
 * loops or nests deeper than {@link TemplateGraph#MAX_DEPTH}; nobody grants a key they don't hold;
 * another company's template answers 404, not 403. Every write refreshes the permission cache.
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    /** Seeded for every new company's Company Admin role; the app finds it by name. */
    public static final Set<String> PROTECTED_SYSTEM_NAMES = Set.of(RoleService.COMPANY_ADMIN_ROLE_NAME);

    private final TemplateRepository templateRepository;
    private final AccessControlRepository accessControlRepository;
    private final CompanyAccessControlRepository companyAccessControlRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VisibilityService visibilityService;
    private final AccessControlCacheService accessControlCacheService;

    // --- templates ---

    /** The system templates plus, for a company (the caller's own, or the one staff named), its templates. */
    @Transactional(readOnly = true)
    public List<TbacTemplateResponse> list(UUID requestedCompanyId) {
        UUID companyId = scopeCompany(requestedCompanyId);
        return toResponses(companyId == null ? templateRepository.findSystem() : templateRepository.findAvailableToCompany(companyId));
    }

    @Transactional(readOnly = true)
    public TbacTemplateResponse get(UUID id) {
        return toResponses(List.of(visibleTemplate(id))).get(0);
    }

    @Transactional
    public TbacTemplateResponse create(UUID requestedCompanyId, TemplateWriteRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new InvalidTemplateException("A template needs a name.");
        }
        UUID companyId = scopeCompany(requestedCompanyId);
        Company company = null;
        if (companyId != null) {
            company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
        } else {
            requirePlatformStaff();
        }
        String name = request.name().trim();
        requireNameFree(company, name, null);
        Template template = new Template(company, name, blankToNull(request.description()));
        template.setIcon(blankToNull(request.icon()));
        template.setPath(blankToNull(request.path()));
        template.setActive(request.active() == null || request.active());
        template.setCreatedByName(caller().getName());
        template.setUpdatedByName(caller().getName());
        templateRepository.saveAndFlush(template);
        return toResponses(List.of(template)).get(0);
    }

    @Transactional
    public TbacTemplateResponse update(UUID id, TemplateWriteRequest request) {
        Template template = requireWritable(id);
        if (request.name() != null) {
            String name = request.name().trim();
            if (isProtectedSystem(template) && !name.equals(template.getName())) {
                throw new ConflictException("\"" + template.getName() + "\" is used to set up new companies and can't be renamed");
            }
            requireNameFree(template.getCompany(), name, template.getId());
            template.setName(name);
        }
        if (request.description() != null) {
            template.setDescription(blankToNull(request.description()));
        }
        if (request.icon() != null) {
            template.setIcon(blankToNull(request.icon()));
        }
        if (request.path() != null) {
            template.setPath(blankToNull(request.path()));
        }
        if (request.active() != null) {
            setActive(template, request.active());
        }
        template.setUpdatedByName(caller().getName());
        accessControlCacheService.invalidateAfterCommit(template.getCompany());
        return toResponses(List.of(template)).get(0);
    }

    /** Templates are never deleted -- a grant may still lean on one -- they are deactivated. */
    @Transactional
    public TbacTemplateResponse deactivate(UUID id) {
        Template template = requireWritable(id);
        setActive(template, false);
        template.setUpdatedByName(caller().getName());
        accessControlCacheService.invalidateAfterCommit(template.getCompany());
        return toResponses(List.of(template)).get(0);
    }

    private void setActive(Template template, boolean active) {
        if (!active && isProtectedSystem(template)) {
            throw new ConflictException("\"" + template.getName() + "\" is used to set up new companies and can't be deactivated");
        }
        template.setActive(active);
    }

    // --- features of a template ---

    @Transactional(readOnly = true)
    public EffectiveFeaturesResponse effectiveFeatures(UUID id) {
        Template template = visibleTemplate(id);
        Set<String> all = accessControlCacheService.loadGraph().resolve(List.of(template.getId()));
        Set<UUID> directIds = template.getAccessControls().stream().map(AccessControl::getId).collect(Collectors.toSet());
        List<EffectiveFeaturesResponse.Entry> direct = new ArrayList<>();
        List<EffectiveFeaturesResponse.Entry> inherited = new ArrayList<>();
        for (AccessControl feature : accessControlRepository.findAll()) {
            if (!all.contains(feature.getKey())) {
                continue;
            }
            (directIds.contains(feature.getId()) ? direct : inherited).add(entry(feature));
        }
        return new EffectiveFeaturesResponse(direct, inherited);
    }

    /** Additive: features the template already has are skipped. */
    @Transactional
    public AddFeaturesResponse addFeatures(UUID id, Set<UUID> featureIds) {
        Template template = requireWritable(id);
        List<AccessControl> features = accessControlRepository.findAllById(featureIds);
        if (features.size() != featureIds.size()) {
            throw new InvalidTemplateException("One or more selected features don't exist.");
        }
        if (!template.isSystem()) {
            Set<UUID> enabled = new HashSet<>(companyAccessControlRepository.findActiveAccessControlIdsByCompanyId(template.getCompany().getId()));
            List<String> notEnabled = features.stream().filter(feature -> !enabled.contains(feature.getId())).map(AccessControl::getKey).toList();
            if (!notEnabled.isEmpty()) {
                throw new InvalidTemplateException(
                        "These features aren't enabled for this company -- add them in the Feature Master first: " + String.join(", ", notEnabled));
            }
        }
        List<AccessControl> fresh = features.stream().filter(feature -> !template.getAccessControls().contains(feature)).toList();
        requireHolds(fresh.stream().map(AccessControl::getKey).collect(Collectors.toSet()));
        template.getAccessControls().addAll(fresh);
        template.setUpdatedByName(caller().getName());
        accessControlCacheService.invalidateAfterCommit(template.getCompany());
        return new AddFeaturesResponse(fresh.size(), features.size() - fresh.size());
    }

    @Transactional
    public void removeFeature(UUID id, UUID featureId) {
        Template template = requireWritable(id);
        template.getAccessControls().removeIf(feature -> feature.getId().equals(featureId));
        template.setUpdatedByName(caller().getName());
        accessControlCacheService.invalidateAfterCommit(template.getCompany());
    }

    // --- templates a template includes ---

    @Transactional(readOnly = true)
    public List<UUID> references(UUID id) {
        return visibleTemplate(id).getReferences().stream().map(Template::getId).toList();
    }

    /**
     * Adds templates to another's includes, each judged on its own (missing, wrong scope, inactive,
     * already included, would loop or nest too deep) so one bad candidate doesn't sink the rest.
     */
    @Transactional
    public AddReferencesResponse addReferences(UUID id, Set<UUID> candidateIds) {
        Template template = requireWritable(id);
        Set<UUID> included = template.getReferences().stream().map(Template::getId).collect(Collectors.toCollection(HashSet::new));
        List<String> errors = new ArrayList<>();
        int added = 0;
        for (UUID candidateId : candidateIds) {
            Optional<Template> candidate = templateRepository.findById(candidateId);
            if (candidate.isEmpty()) {
                errors.add(candidateId + ": it doesn't exist");
                continue;
            }
            String reason = rejectionOf(template, candidate.get(), included);
            if (reason == null) {
                reason = lackedBy(candidate.get());
            }
            if (reason == null) {
                Set<UUID> proposed = new HashSet<>(included);
                proposed.add(candidateId);
                if (!accessControlCacheService.loadGraph().allowsReferences(id, proposed)) {
                    reason = "it would loop back on this template, or nest deeper than " + TemplateGraph.MAX_DEPTH + " templates";
                }
            }
            if (reason != null) {
                errors.add(candidate.get().getName() + ": " + reason);
                continue;
            }
            template.getReferences().add(candidate.get());
            included.add(candidateId);
            templateRepository.saveAndFlush(template);
            added++;
        }
        template.setUpdatedByName(caller().getName());
        accessControlCacheService.invalidateAfterCommit(template.getCompany());
        return new AddReferencesResponse(added, candidateIds.size(), errors);
    }

    @Transactional
    public void removeReference(UUID id, UUID referencedId) {
        Template template = requireWritable(id);
        template.getReferences().removeIf(reference -> reference.getId().equals(referencedId));
        template.setUpdatedByName(caller().getName());
        accessControlCacheService.invalidateAfterCommit(template.getCompany());
    }

    private String rejectionOf(Template template, Template candidate, Set<UUID> included) {
        if (candidate.getId().equals(template.getId())) {
            return "a template can't include itself";
        }
        if (!mayInclude(template, candidate)) {
            return "it belongs to another company";
        }
        if (!candidate.isActive()) {
            return "it is inactive";
        }
        if (included.contains(candidate.getId())) {
            return "it is already included";
        }
        return null;
    }

    /** Why the caller may not include the template (it grants keys they don't hold), or null. */
    private String lackedBy(Template candidate) {
        if (caller().getCompany() == null) {
            return null;
        }
        Set<String> lacking = accessControlCacheService.loadGraph().resolve(List.of(candidate.getId()));
        lacking.removeAll(accessControlCacheService.resolveEffectiveKeys(caller()));
        return lacking.isEmpty() ? null : "it grants access you don't hold yourself";
    }

    // --- templates of a role ---

    @Transactional(readOnly = true)
    public List<TbacTemplateResponse> roleTemplates(UUID roleId) {
        return toResponses(List.copyOf(visibleRole(roleId).getTemplates()));
    }

    @Transactional
    public void assignRoleTemplates(UUID roleId, Set<UUID> templateIds) {
        Role role = visibleRole(roleId);
        List<Template> wanted = requireAssignable(role.getCompany().getId(), templateIds);
        List<Template> fresh = wanted.stream().filter(template -> !role.getTemplates().contains(template)).toList();
        requireGrantable(fresh);
        role.getTemplates().addAll(fresh);
        accessControlCacheService.invalidateAfterCommit(role.getCompany());
    }

    @Transactional
    public void removeRoleTemplate(UUID roleId, UUID templateId) {
        Role role = visibleRole(roleId);
        Set<UUID> left = role.getTemplates().stream().map(Template::getId).filter(id -> !id.equals(templateId)).collect(Collectors.toSet());
        if (left.isEmpty() && RoleService.COMPANY_ADMIN_ROLE_NAME.equals(role.getName())) {
            // Company Admin repairs access for everyone; emptying it would lock the company out of that.
            throw new ConflictException("\"" + role.getName() + "\" must keep at least one template");
        }
        role.getTemplates().removeIf(template -> template.getId().equals(templateId));
        accessControlCacheService.invalidateAfterCommit(role.getCompany());
    }

    // --- templates of a user (the exception to "templates come from the role") ---

    @Transactional(readOnly = true)
    public List<TbacTemplateResponse> userTemplates(UUID userId) {
        return toResponses(List.copyOf(visibleUser(userId).getTemplates()));
    }

    @Transactional
    public void assignUserTemplates(UUID userId, Set<UUID> templateIds) {
        User user = visibleUser(userId);
        List<Template> wanted = requireAssignable(user.getCompany().getId(), templateIds);
        List<Template> fresh = wanted.stream().filter(template -> !user.getTemplates().contains(template)).toList();
        requireGrantable(fresh);
        user.getTemplates().addAll(fresh);
        accessControlCacheService.invalidateAfterCommit(user.getCompany());
    }

    @Transactional
    public void removeUserTemplate(UUID userId, UUID templateId) {
        User user = visibleUser(userId);
        user.getTemplates().removeIf(template -> template.getId().equals(templateId));
        accessControlCacheService.invalidateAfterCommit(user.getCompany());
    }

    /** Gives several users the same templates, additively; a user who can't be updated fails alone. */
    @Transactional
    public BulkAssignUserTemplatesResponse bulkAssignUserTemplates(Set<UUID> userIds, Set<UUID> templateIds) {
        Company company = caller().getCompany();
        List<BulkAssignUserTemplatesResponse.UserResult> results = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        List<Template> templates = null;
        UUID templatesCompany = null;
        for (UUID userId : userIds) {
            User user = userRepository.findById(userId).orElse(null);
            String failure = failureOf(user, company);
            if (failure != null) {
                results.add(new BulkAssignUserTemplatesResponse.UserResult(userId, user != null ? user.getName() : null, 0, 0, "failed", failure));
                continue;
            }
            if (templates == null || !user.getCompany().getId().equals(templatesCompany)) {
                templatesCompany = user.getCompany().getId();
                templates = requireAssignable(templatesCompany, templateIds);
                requireGrantable(templates);
            }
            int added = 0;
            for (Template template : templates) {
                if (user.getTemplates().add(template)) {
                    added++;
                }
            }
            userRepository.save(user);
            created += added;
            skipped += templates.size() - added;
            results.add(new BulkAssignUserTemplatesResponse.UserResult(userId, user.getName(), added, templates.size() - added, "success", null));
        }
        accessControlCacheService.invalidateAfterCommit(company);
        int failed = (int) results.stream().filter(result -> "failed".equals(result.status())).count();
        return new BulkAssignUserTemplatesResponse(results.size(), results.size() - failed, failed, created, skipped, results);
    }

    private String failureOf(User user, Company callerCompany) {
        if (user == null || user.getCompany() == null) {
            return "User not found";
        }
        if (callerCompany != null && !user.getCompany().getId().equals(callerCompany.getId())) {
            return "User not found";
        }
        if (callerCompany != null && !visibilityService.resolve(caller()).canSeeUser(user)) {
            return "User not found";
        }
        return null;
    }

    // --- rules ---

    /** The templates a company may map to its roles and users: active, and either system or its own. */
    List<Template> requireAssignable(UUID companyId, Collection<UUID> templateIds) {
        List<Template> templates = templateRepository.findAllById(templateIds);
        if (templates.size() != new HashSet<>(templateIds).size()) {
            throw new InvalidTemplateException("One or more selected templates don't exist.");
        }
        for (Template template : templates) {
            boolean ownOrSystem = template.isSystem() || template.getCompany().getId().equals(companyId);
            if (!ownOrSystem || !template.isActive()) {
                throw new InvalidTemplateException("Template \"" + template.getName() + "\" can't be used here.");
            }
        }
        return templates;
    }

    /**
     * Nobody grants what they don't hold: the keys the given templates would add (followed through
     * what they include) must all be among the caller's own. Platform staff are exempt. Callers pass
     * only what is being NEWLY granted, so editing around a template one can't grant still works.
     */
    void requireGrantable(Collection<Template> templates) {
        if (templates.isEmpty() || caller().getCompany() == null) {
            return;
        }
        requireHolds(accessControlCacheService.loadGraph().resolve(templates.stream().map(Template::getId).toList()));
    }

    private void requireHolds(Set<String> keys) {
        if (keys.isEmpty() || caller().getCompany() == null) {
            return;
        }
        Set<String> lacking = new TreeSet<>(keys);
        lacking.removeAll(accessControlCacheService.resolveEffectiveKeys(caller()));
        if (!lacking.isEmpty()) {
            throw new AccessDeniedException("You can only grant access you hold yourself -- you lack: " + String.join(", ", lacking));
        }
    }

    /** A system template includes only system ones; a company's includes system ones and its own. */
    private static boolean mayInclude(Template template, Template reference) {
        return template.isSystem()
                ? reference.isSystem()
                : reference.isSystem() || reference.getCompany().getId().equals(template.getCompany().getId());
    }

    private void requireNameFree(Company company, String name, UUID selfId) {
        Optional<Template> existing = company == null
                ? templateRepository.findSystemByName(name)
                : templateRepository.findByCompanyIdAndName(company.getId(), name);
        if (existing.isPresent() && !existing.get().getId().equals(selfId)) {
            throw new ConflictException("A template named \"" + name + "\" already exists");
        }
    }

    /** The template for a write: another company's is 404; a system template needs platform staff. */
    private Template requireWritable(UUID id) {
        Template template = templateRepository.findById(id).orElseThrow(() -> new TemplateNotFoundException(id));
        User caller = caller();
        if (template.isSystem()) {
            if (caller.getCompany() != null) {
                throw new AccessDeniedException("System templates can only be changed by platform staff");
            }
        } else if (caller.getCompany() != null && !caller.getCompany().getId().equals(template.getCompany().getId())) {
            throw new TemplateNotFoundException(id);
        }
        return template;
    }

    /** A template the caller may read: a system one, or one of their company (platform staff: any). */
    private Template visibleTemplate(UUID id) {
        Template template = templateRepository.findById(id).orElseThrow(() -> new TemplateNotFoundException(id));
        Company own = caller().getCompany();
        if (own != null && !template.isSystem() && !template.getCompany().getId().equals(own.getId())) {
            throw new TemplateNotFoundException(id);
        }
        return template;
    }

    private Role visibleRole(UUID roleId) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RoleNotFoundException(roleId));
        Company own = caller().getCompany();
        if (role.getCompany() == null || (own != null && !role.getCompany().getId().equals(own.getId()))) {
            throw new RoleNotFoundException(roleId);
        }
        return role;
    }

    /** A user of the caller's company the caller can see (see Visibility); platform staff see any company user. */
    private User visibleUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        Company own = caller().getCompany();
        if (user.getCompany() == null
                || (own != null && (!user.getCompany().getId().equals(own.getId()) || !visibilityService.resolve(caller()).canSeeUser(user)))) {
            throw new UserNotFoundException(userId);
        }
        return user;
    }

    private boolean isProtectedSystem(Template template) {
        return template.isSystem() && PROTECTED_SYSTEM_NAMES.contains(template.getName());
    }

    private void requirePlatformStaff() {
        if (caller().getCompany() != null) {
            throw new AccessDeniedException("Platform staff only");
        }
    }

    /** For operations that need a company: the caller's own, or the one platform staff named. */
    public UUID requireCompanyScope(UUID requested) {
        UUID companyId = scopeCompany(requested);
        if (companyId == null) {
            throw new InvalidTemplateException("Choose a company first.");
        }
        return companyId;
    }

    /** The company an operation applies to: a company user's own, whatever they ask for; staff name it (or none). */
    private UUID scopeCompany(UUID requested) {
        Company own = caller().getCompany();
        return own != null ? own.getId() : requested;
    }

    private User caller() {
        UUID callerId = CurrentUser.id();
        return userRepository.findById(callerId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + callerId));
    }

    private static EffectiveFeaturesResponse.Entry entry(AccessControl feature) {
        return new EffectiveFeaturesResponse.Entry(feature.getId(), feature.getKey(), feature.getCategory(), feature.getName());
    }

    private List<TbacTemplateResponse> toResponses(List<Template> templates) {
        TemplateGraph graph = accessControlCacheService.loadGraph();
        Map<UUID, Integer> counts = new HashMap<>();
        for (Template template : templates) {
            counts.put(template.getId(), graph.resolve(List.of(template.getId())).size());
        }
        return templates.stream()
                .sorted(Comparator.comparing(Template::getName, String.CASE_INSENSITIVE_ORDER))
                .map(template -> TbacTemplateResponse.from(template, counts.get(template.getId())))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
