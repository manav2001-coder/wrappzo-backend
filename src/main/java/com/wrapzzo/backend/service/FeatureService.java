package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.BulkRegisterFeaturesResponse;
import com.wrapzzo.backend.dto.TbacCatalogueEntry;
import com.wrapzzo.backend.dto.TbacFeatureResponse;
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
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The feature master, per company. The permission keys live in code (security.Permission, checked
 * against the database at startup), so a company's master lists the keys it has registered and
 * whether each is switched on -- registering adds a key to the company, never invents one.
 * Registering only ever adds, so a feature an admin switched off stays off across restarts.
 *
 * <p>The company an operation applies to is the caller's own; platform staff, who belong to none,
 * name it on the request.
 */
@Service
@RequiredArgsConstructor
public class FeatureService {

    /** Modules that run the access screens themselves: switching one off would lock the company out of repairing access. */
    private static final Set<String> ALWAYS_ON_PREFIXES = Set.of("tbac.", "role.");

    private final AccessControlRepository accessControlRepository;
    private final CompanyAccessControlRepository companyAccessControlRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AccessControlCacheService accessControlCacheService;

    /** The features the company has registered, on or off. */
    @Transactional(readOnly = true)
    public List<TbacFeatureResponse> list(UUID requestedCompanyId) {
        UUID companyId = scopeCompany(requestedCompanyId);
        return companyAccessControlRepository.findByCompanyId(companyId).stream()
                .sorted(Comparator.comparing((CompanyAccessControl row) -> row.getAccessControl().getSortOrder()))
                .map(TbacFeatureResponse::from)
                .toList();
    }

    /** Every key a company could register -- what the "register features" dialog offers. */
    @Transactional(readOnly = true)
    public List<TbacCatalogueEntry> catalogue() {
        return accessControlRepository.findByCompanyAssignableTrue().stream()
                .sorted(Comparator.comparingInt(AccessControl::getSortOrder))
                .map(TbacCatalogueEntry::from)
                .toList();
    }

    /** Registers the given keys for the company; ones it already has are skipped. */
    @Transactional
    public BulkRegisterFeaturesResponse register(UUID requestedCompanyId, Collection<String> keys) {
        Company company = company(scopeCompany(requestedCompanyId));
        List<AccessControl> wanted = accessControlRepository.findByKeyIn(keys);
        if (wanted.size() != new HashSet<>(keys).size()) {
            throw new InvalidTemplateException("One or more of those features don't exist.");
        }
        List<String> platformOnly = wanted.stream().filter(feature -> !feature.isCompanyAssignable()).map(AccessControl::getKey).toList();
        if (!platformOnly.isEmpty()) {
            throw new InvalidTemplateException("These features are platform-only and can't be registered for a company: " + String.join(", ", platformOnly));
        }
        int added = addMissing(company, wanted, caller().getName());
        accessControlCacheService.invalidateAfterCommit(company);
        return new BulkRegisterFeaturesResponse(added, wanted.size() - added);
    }

    @Transactional
    public TbacFeatureResponse setActive(UUID requestedCompanyId, UUID featureId, boolean active) {
        UUID companyId = scopeCompany(requestedCompanyId);
        CompanyAccessControl row = companyAccessControlRepository.findByCompanyIdAndAccessControlId(companyId, featureId)
                .orElseThrow(() -> new InvalidTemplateException("That feature isn't registered for this company -- add it first."));
        AccessControl feature = row.getAccessControl();
        if (!active && ALWAYS_ON_PREFIXES.stream().anyMatch(prefix -> feature.getKey().startsWith(prefix))) {
            throw new ConflictException("\"" + feature.getName() + "\" runs the access screens and can't be switched off");
        }
        row.setActive(active);
        row.setUpdatedOn(Instant.now());
        row.setUpdatedByName(caller().getName());
        accessControlCacheService.invalidateAfterCommit(row.getCompany());
        return TbacFeatureResponse.from(row);
    }

    /** A new company starts with every feature it can hold. */
    @Transactional
    public void registerAll(Company company) {
        addMissing(company, accessControlRepository.findByCompanyAssignableTrue(), null);
    }

    /** Boot-time catch-up (CompanyFeatureRegistrar): a key added in a release reaches companies that predate it. */
    @Transactional
    public int registerMissingForAllCompanies() {
        List<AccessControl> catalogue = accessControlRepository.findByCompanyAssignableTrue();
        int added = 0;
        for (Company company : companyRepository.findAll()) {
            int forCompany = addMissing(company, catalogue, null);
            if (forCompany > 0) {
                accessControlCacheService.invalidateAfterCommit(company);
            }
            added += forCompany;
        }
        return added;
    }

    private int addMissing(Company company, List<AccessControl> features, String actorName) {
        Set<UUID> have = new HashSet<>(
                companyAccessControlRepository.findByCompanyId(company.getId()).stream().map(row -> row.getAccessControl().getId()).toList());
        int added = 0;
        for (AccessControl feature : features) {
            if (have.add(feature.getId())) {
                CompanyAccessControl row = new CompanyAccessControl(company, feature);
                row.setAddedByName(actorName);
                row.setUpdatedByName(actorName);
                companyAccessControlRepository.save(row);
                added++;
            }
        }
        return added;
    }

    /** The company an operation applies to: a company user's own, whatever they ask for; platform staff must name one. */
    private UUID scopeCompany(UUID requested) {
        Company own = caller().getCompany();
        UUID companyId = own != null ? own.getId() : requested;
        if (companyId == null) {
            throw new InvalidTemplateException("Choose a company first.");
        }
        return companyId;
    }

    private Company company(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
    }

    private User caller() {
        UUID callerId = CurrentUser.id();
        return userRepository.findById(callerId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + callerId));
    }
}
