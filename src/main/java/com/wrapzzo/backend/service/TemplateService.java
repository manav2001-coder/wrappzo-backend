package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.AddReferencesResponse;
import com.wrapzzo.backend.dto.BulkUserTemplatesResponse;
import com.wrapzzo.backend.dto.TemplateRequest;
import com.wrapzzo.backend.dto.TemplateResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.InvalidTemplateException;
import com.wrapzzo.backend.exception.TemplateNotFoundException;
import com.wrapzzo.backend.exception.UserNotFoundException;
import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Template;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyAccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access templates: system masters (no company, platform staff only) and each company's own.
 * Templates are the only thing that grants, so every write refreshes the permission cache. Rules
 * enforced here, not in the UI: a company template holds only company-assignable keys, includes
 * only system templates or its own company's, and never loops or nests deeper than
 * {@link TemplateGraph#MAX_DEPTH}; another company's template answers 404, not 403.
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
    private final CompanyAccessGuard companyAccessGuard;
    private final AccessControlCacheService accessControlCacheService;

    /** A company's own templates plus every system master. */
    @Transactional(readOnly = true)
    public List<TemplateResponse> listForCompany(UUID companyId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        return toResponses(templateRepository.findAvailableToCompany(companyId));
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listSystem() {
        requirePlatformStaff();
        return toResponses(templateRepository.findSystem());
    }

    @Transactional
    public TemplateResponse create(UUID companyId, TemplateRequest request) {
        Company company = null;
        if (companyId != null) {
            companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
            company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
        } else {
            requirePlatformStaff();
        }
        String name = request.name().trim();
        requireNameFree(company, name, null);
        Template template = new Template(company, name, blankToNull(request.description()));
        template.setActive(request.active() == null || request.active());
        templateRepository.saveAndFlush(template);
        applyContents(template, request);
        accessControlCacheService.invalidateAfterCommit();
        return toResponse(template);
    }

    @Transactional
    public TemplateResponse update(UUID id, TemplateRequest request) {
        Template template = requireWritable(id);
        String name = request.name().trim();
        if (isProtectedSystem(template) && !name.equals(template.getName())) {
            throw new ConflictException("\"" + template.getName() + "\" is used to set up new companies and can't be renamed");
        }
        requireNameFree(template.getCompany(), name, template.getId());
        template.setName(name);
        template.setDescription(blankToNull(request.description()));
        template.setActive(request.active() == null || request.active());
        applyContents(template, request);
        accessControlCacheService.invalidateAfterCommit();
        return toResponse(template);
    }

    /** A copy of any template the caller can see, into the target company (or the system set for platform staff). */
    @Transactional
    public TemplateResponse clone(UUID companyId, UUID sourceId, String name) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
        Template source = templateRepository.findById(sourceId).orElseThrow(() -> new TemplateNotFoundException(sourceId));
        if (!source.isSystem() && !source.getCompany().getId().equals(companyId)) {
            throw new TemplateNotFoundException(sourceId);
        }
        String copyName = name.trim();
        requireNameFree(company, copyName, null);
        Template copy = new Template(company, copyName, source.getDescription());
        copy.setActive(true);
        templateRepository.saveAndFlush(copy);
        // Features the company hasn't enabled are left out of the copy rather than refusing the clone.
        Set<UUID> enabled = new HashSet<>(companyAccessControlRepository.findActiveAccessControlIdsByCompanyId(companyId));
        copy.getAccessControls().addAll(source.getAccessControls().stream().filter(ac -> enabled.contains(ac.getId())).toList());
        copy.getReferences().addAll(source.getReferences().stream().filter(Template::isActive).toList());
        accessControlCacheService.invalidateAfterCommit();
        return toResponse(copy);
    }

    @Transactional
    public void delete(UUID id) {
        Template template = requireWritable(id);
        if (isProtectedSystem(template)) {
            throw new ConflictException("\"" + template.getName() + "\" is used to set up new companies and can't be removed");
        }
        long uses = templateRepository.countRoleUses(id) + templateRepository.countUserUses(id) + templateRepository.countReferencedBy(id);
        if (uses > 0) {
            throw new ConflictException("This template is still used by roles, users or other templates -- unmap it first, or deactivate it instead");
        }
        templateRepository.delete(template);
        accessControlCacheService.invalidateAfterCommit();
    }

    /**
     * Adds templates to another's includes, each judged on its own (missing, wrong scope, inactive,
     * already included, would loop or nest too deep) so one bad candidate doesn't sink the rest.
     */
    @Transactional
    public AddReferencesResponse addReferences(UUID id, Set<UUID> candidateIds) {
        Template template = requireWritable(id);
        Set<UUID> included = template.getReferences().stream().map(Template::getId).collect(Collectors.toCollection(HashSet::new));
        List<AddReferencesResponse.Rejection> errors = new ArrayList<>();
        int added = 0;
        for (UUID candidateId : candidateIds) {
            Optional<Template> candidate = templateRepository.findById(candidateId);
            String reason = candidate.map(c -> rejectionOf(template, c, included)).orElse("It doesn't exist");
            if (reason != null) {
                errors.add(new AddReferencesResponse.Rejection(candidateId, candidate.map(Template::getName).orElse(null), reason));
                continue;
            }
            Set<UUID> proposed = new HashSet<>(included);
            proposed.add(candidateId);
            if (!accessControlCacheService.loadGraph().allowsReferences(id, proposed)) {
                errors.add(new AddReferencesResponse.Rejection(candidateId, candidate.get().getName(),
                        "It would loop back on this template, or nest deeper than " + TemplateGraph.MAX_DEPTH + " templates"));
                continue;
            }
            template.getReferences().add(candidate.get());
            included.add(candidateId);
            templateRepository.saveAndFlush(template);
            added++;
        }
        accessControlCacheService.invalidateAfterCommit();
        return new AddReferencesResponse(added, candidateIds.size(), errors, toResponse(template));
    }

    @Transactional
    public TemplateResponse removeReference(UUID id, UUID referencedId) {
        Template template = requireWritable(id);
        template.getReferences().removeIf(reference -> reference.getId().equals(referencedId));
        accessControlCacheService.invalidateAfterCommit();
        return toResponse(template);
    }

    private String rejectionOf(Template template, Template candidate, Set<UUID> included) {
        if (!mayInclude(template, candidate)) {
            return "It belongs to another company";
        }
        if (!candidate.isActive()) {
            return "It is inactive";
        }
        if (included.contains(candidate.getId())) {
            return "It is already included";
        }
        return null;
    }

    // --- user-level templates (the exception to "templates come from the role") ---

    @Transactional(readOnly = true)
    public Set<UUID> userTemplateIds(UUID companyId, UUID userId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        return new HashSet<>(templateRepository.findTemplateIdsByUserId(companyUser(companyId, userId).getId()));
    }

    @Transactional
    public Set<UUID> replaceUserTemplates(UUID companyId, UUID userId, Set<UUID> templateIds) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        User user = companyUser(companyId, userId);
        user.getTemplates().clear();
        user.getTemplates().addAll(requireAssignable(companyId, templateIds));
        userRepository.save(user);
        accessControlCacheService.invalidateAfterCommit();
        return templateIds;
    }

    /** Gives several users the same templates, additively; a user who can't be updated fails alone. */
    @Transactional
    public BulkUserTemplatesResponse bulkAssignUserTemplates(UUID companyId, Set<UUID> userIds, Set<UUID> templateIds) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        List<Template> templates = requireAssignable(companyId, templateIds);
        List<BulkUserTemplatesResponse.UserResult> results = new ArrayList<>();
        for (UUID userId : userIds) {
            User user = userRepository.findById(userId)
                    .filter(candidate -> candidate.getCompany() != null && candidate.getCompany().getId().equals(companyId))
                    .orElse(null);
            if (user == null) {
                results.add(new BulkUserTemplatesResponse.UserResult(userId, null, 0, 0, "failed", "User not found in this company"));
                continue;
            }
            int added = 0;
            for (Template template : templates) {
                if (user.getTemplates().add(template)) {
                    added++;
                }
            }
            userRepository.save(user);
            results.add(new BulkUserTemplatesResponse.UserResult(userId, user.getName(), added, templates.size() - added, "ok", null));
        }
        accessControlCacheService.invalidateAfterCommit();
        return new BulkUserTemplatesResponse(results);
    }

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

    // --- internal ---

    private void applyContents(Template template, TemplateRequest request) {
        List<AccessControl> accessControls = withImpliedViews(accessControlRepository.findAllById(request.accessControlIds()));
        if (accessControls.stream().map(AccessControl::getId).filter(request.accessControlIds()::contains).count() != request.accessControlIds().size()) {
            throw new InvalidTemplateException("One or more selected access-control keys don't exist.");
        }
        if (!template.isSystem()) {
            Set<UUID> enabled = new HashSet<>(companyAccessControlRepository.findActiveAccessControlIdsByCompanyId(template.getCompany().getId()));
            List<String> notEnabled = accessControls.stream().filter(ac -> !enabled.contains(ac.getId())).map(AccessControl::getKey).toList();
            if (!notEnabled.isEmpty()) {
                throw new InvalidTemplateException(
                        "These features aren't enabled for this company -- add them in the Feature Master first: " + String.join(", ", notEnabled));
            }
        }
        List<Template> references = templateRepository.findAllById(request.referencedTemplateIds());
        if (references.size() != request.referencedTemplateIds().size()) {
            throw new InvalidTemplateException("One or more included templates don't exist.");
        }
        for (Template reference : references) {
            if (!mayInclude(template, reference)) {
                throw new InvalidTemplateException("Template \"" + reference.getName() + "\" can't be included here.");
            }
        }
        if (!accessControlCacheService.loadGraph().allowsReferences(template.getId(), request.referencedTemplateIds())) {
            throw new InvalidTemplateException(
                    "Those inclusions would loop, or nest deeper than " + TemplateGraph.MAX_DEPTH + " templates.");
        }
        template.getAccessControls().clear();
        template.getAccessControls().addAll(accessControls);
        template.getReferences().clear();
        template.getReferences().addAll(references);
    }

    /** A system template includes only system ones; a company's includes system ones and its own. */
    private static boolean mayInclude(Template template, Template reference) {
        return template.isSystem()
                ? reference.isSystem()
                : reference.isSystem() || reference.getCompany().getId().equals(template.getCompany().getId());
    }

    /** Any action of a module brings its view with it ("Edit campaigns" without "View campaigns" isn't a usable grant). */
    private List<AccessControl> withImpliedViews(List<AccessControl> selected) {
        Set<String> keys = selected.stream().map(AccessControl::getKey).collect(Collectors.toSet());
        List<AccessControl> all = new ArrayList<>(selected);
        for (AccessControl accessControl : selected) {
            Optional<Permission> view = Permission.fromKey(accessControl.getKey()).flatMap(Permission::impliedView);
            if (view.isPresent() && keys.add(view.get().key())) {
                accessControlRepository.findByKey(view.get().key()).ifPresent(all::add);
            }
        }
        return all;
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
                throw new AccessDeniedException("System templates can only be changed by platform staff -- clone it to change a copy");
            }
        } else if (caller.getCompany() != null && !caller.getCompany().getId().equals(template.getCompany().getId())) {
            throw new TemplateNotFoundException(id);
        }
        return template;
    }

    private boolean isProtectedSystem(Template template) {
        return template.isSystem() && PROTECTED_SYSTEM_NAMES.contains(template.getName());
    }

    private void requirePlatformStaff() {
        if (caller().getCompany() != null) {
            throw new AccessDeniedException("Platform staff only");
        }
    }

    private User caller() {
        UUID callerId = CurrentUser.id();
        return userRepository.findById(callerId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + callerId));
    }

    private User companyUser(UUID companyId, UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getCompany() != null && user.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private TemplateResponse toResponse(Template template) {
        return toResponses(List.of(template)).get(0);
    }

    private List<TemplateResponse> toResponses(List<Template> templates) {
        TemplateGraph graph = accessControlCacheService.loadGraph();
        Map<String, UUID> idByKey = new HashMap<>();
        accessControlRepository.findAll().forEach(ac -> idByKey.put(ac.getKey(), ac.getId()));
        return templates.stream()
                .map(template -> TemplateResponse.from(
                        template,
                        graph.resolve(List.of(template.getId())).stream().map(idByKey::get).filter(java.util.Objects::nonNull).collect(Collectors.toSet()),
                        templateRepository.countRoleUses(template.getId()),
                        templateRepository.countUserUses(template.getId())))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
