package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.AddCompanyVendorRequest;
import com.wrapzzo.backend.dto.AddVendorRequest;
import com.wrapzzo.backend.dto.AvailableVendorResponse;
import com.wrapzzo.backend.dto.SetVendorCoverageRequest;
import com.wrapzzo.backend.exception.InvalidVendorCoverageException;
import com.wrapzzo.backend.model.VendorStatus;
import java.util.Comparator;
import com.wrapzzo.backend.dto.CompanyVendorResponse;
import com.wrapzzo.backend.dto.VendorCoverageRequest;
import com.wrapzzo.backend.dto.VendorCoverageResponse;
import com.wrapzzo.backend.exception.ConflictException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.CompanyVendorNotFoundException;
import com.wrapzzo.backend.exception.OrgUnitNotFoundException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which vendors serve a company -- this roster is what scopes that company's Gift Store /
 * Quick Gift catalog (see GiftService.listActiveForUser, V31/V33). A mapping is either
 * company-wide (orgUnitId null) or scoped to one org unit and everything beneath it (e.g. a
 * vendor that only serves "Zone North"). list()/assign()/unassign() are the admin-only
 * (company.manage) path, operating on any company; createForCompany() is the narrower
 * self-service path (company.vendor.manage) a Company Admin uses for their own.
 */
@Service
@RequiredArgsConstructor
public class CompanyVendorService {

    private final CompanyVendorRepository companyVendorRepository;
    private final CompanyRepository companyRepository;
    private final VendorRepository vendorRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final VendorService vendorService;

    @Transactional(readOnly = true)
    public List<CompanyVendorResponse> list(UUID companyId) {
        requireCompany(companyId);
        return companyVendorRepository.findByCompany_IdOrderByCreatedAtDesc(companyId).stream()
                .map(CompanyVendorResponse::from)
                .toList();
    }

    @Transactional
    public List<CompanyVendorResponse> assign(UUID companyId, UUID vendorId, UUID orgUnitId) {
        Company company = requireCompany(companyId);
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow(() -> new VendorNotFoundException(vendorId));
        // The org unit must belong to THIS company: a vendor scoped to another company's unit would be
        // invisible here and would leak this company's roster into theirs.
        OrgUnit orgUnit = orgUnitId != null
                ? orgUnitRepository.findById(orgUnitId)
                        .filter(unit -> unit.getCompany().getId().equals(companyId))
                        .orElseThrow(() -> new OrgUnitNotFoundException(orgUnitId))
                : null;
        try {
            companyVendorRepository.save(new CompanyVendor(company, vendor, orgUnit));
        } catch (DataIntegrityViolationException e) {
            // Already mapped this exact way (same vendor, same org-unit scope) -- idempotent, not an error.
        }
        return list(companyId);
    }

    /** The vendors a company admin may map: every active one, by name only. */
    @Transactional(readOnly = true)
    public List<AvailableVendorResponse> availableVendors() {
        return vendorRepository.findByStatus(VendorStatus.ACTIVE).stream()
                .map(vendor -> new AvailableVendorResponse(vendor.getId(), vendor.getName()))
                .sorted(Comparator.comparing(AvailableVendorResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Sets where one vendor serves inside this company -- the whole company, or exactly the org units named (a division,
     * or any unit within one; a unit covers everything beneath it). Replaces this vendor's earlier mappings in the
     * company; its mappings in other companies are never touched.
     */
    @Transactional
    public List<CompanyVendorResponse> setCoverage(UUID companyId, UUID vendorId, SetVendorCoverageRequest request) {
        Company company = requireCompany(companyId);
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow(() -> new VendorNotFoundException(vendorId));
        List<UUID> unitIds = request.orgUnitIds().stream().distinct().toList();
        if (request.wholeCompany() && !unitIds.isEmpty()) {
            throw new InvalidVendorCoverageException("Choose either the whole company or specific divisions and org units, not both.");
        }
        if (!request.wholeCompany() && unitIds.isEmpty()) {
            throw new InvalidVendorCoverageException("Choose at least one division or org unit for " + vendor.getName() + ", or the whole company.");
        }
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw new InvalidVendorCoverageException(vendor.getName() + " is suspended, so it can't be mapped to anything until it is active again.");
        }
        Map<UUID, OrgUnit> units = new LinkedHashMap<>();
        for (UUID unitId : unitIds) {
            OrgUnit unit = orgUnitRepository.findById(unitId)
                    .filter(candidate -> candidate.getCompany().getId().equals(companyId))
                    .orElseThrow(() -> new OrgUnitNotFoundException(unitId));
            units.put(unitId, unit);
        }
        Set<UUID> have = new HashSet<>();
        boolean haveWholeCompany = false;
        for (CompanyVendor mapping : companyVendorRepository.findByVendor_Id(vendorId)) {
            if (!mapping.getCompany().getId().equals(companyId)) {
                continue;
            }
            UUID unitId = mapping.getOrgUnit() != null ? mapping.getOrgUnit().getId() : null;
            boolean keep = unitId == null ? request.wholeCompany() : units.containsKey(unitId);
            if (!keep) {
                companyVendorRepository.delete(mapping);
            } else if (unitId == null) {
                haveWholeCompany = true;
            } else {
                have.add(unitId);
            }
        }
        companyVendorRepository.flush();
        if (request.wholeCompany() && !haveWholeCompany) {
            companyVendorRepository.save(new CompanyVendor(company, vendor, null));
        }
        for (OrgUnit unit : units.values()) {
            if (!have.contains(unit.getId())) {
                companyVendorRepository.save(new CompanyVendor(company, vendor, unit));
            }
        }
        return list(companyId);
    }

    /** Takes a vendor off this company altogether -- every mapping it has here. Orders it already holds stay with it. */
    @Transactional
    public List<CompanyVendorResponse> removeVendor(UUID companyId, UUID vendorId) {
        requireCompany(companyId);
        List<CompanyVendor> mappings = companyVendorRepository.findByVendor_Id(vendorId).stream()
                .filter(mapping -> mapping.getCompany().getId().equals(companyId))
                .toList();
        if (mappings.isEmpty()) {
            throw new VendorNotFoundException(vendorId);
        }
        companyVendorRepository.deleteAll(mappings);
        return list(companyId);
    }

    // --- the vendor's side: where it serves ---

    /** The companies a vendor serves and, for each, the org units it is limited to (none = the whole company). */
    @Transactional(readOnly = true)
    public VendorCoverageResponse coverage(UUID vendorId) {
        vendorRepository.findById(vendorId).orElseThrow(() -> new VendorNotFoundException(vendorId));
        Map<UUID, VendorCoverageResponse.CompanyCoverage> byCompany = new LinkedHashMap<>();
        for (CompanyVendor mapping : companyVendorRepository.findByVendor_Id(vendorId)) {
            VendorCoverageResponse.CompanyCoverage entry = byCompany.computeIfAbsent(
                    mapping.getCompany().getId(),
                    id -> new VendorCoverageResponse.CompanyCoverage(id, mapping.getCompany().getName(), new ArrayList<>()));
            if (mapping.getOrgUnit() != null) {
                entry.orgUnitIds().add(mapping.getOrgUnit().getId());
            }
        }
        // A company-wide mapping makes the company served in whole, whatever units are also listed.
        for (CompanyVendor mapping : companyVendorRepository.findByVendor_Id(vendorId)) {
            if (mapping.getOrgUnit() == null) {
                byCompany.get(mapping.getCompany().getId()).orgUnitIds().clear();
            }
        }
        return new VendorCoverageResponse(List.copyOf(byCompany.values()));
    }

    /**
     * Replaces where a vendor serves: the companies listed, each whole or limited to the org units
     * named. Anything the vendor served that is no longer listed is removed; nothing else is touched.
     */
    @Transactional
    public VendorCoverageResponse replaceCoverage(UUID vendorId, VendorCoverageRequest request) {
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow(() -> new VendorNotFoundException(vendorId));
        Set<String> wanted = new HashSet<>();
        Set<UUID> seenCompanies = new HashSet<>();
        for (VendorCoverageRequest.Entry entry : request.companies()) {
            if (!seenCompanies.add(entry.companyId())) {
                throw new ConflictException("A company is listed twice");
            }
            Company company = requireCompany(entry.companyId());
            List<UUID> unitIds = entry.orgUnitIds() == null ? List.of() : entry.orgUnitIds();
            if (unitIds.isEmpty()) {
                wanted.add(key(company.getId(), null));
            }
            for (UUID unitId : unitIds) {
                orgUnitRepository.findById(unitId)
                        .filter(unit -> unit.getCompany().getId().equals(company.getId()))
                        .orElseThrow(() -> new OrgUnitNotFoundException(unitId));
                wanted.add(key(company.getId(), unitId));
            }
        }
        Set<String> have = new HashSet<>();
        for (CompanyVendor mapping : companyVendorRepository.findByVendor_Id(vendorId)) {
            String mappingKey = key(mapping.getCompany().getId(), mapping.getOrgUnit() != null ? mapping.getOrgUnit().getId() : null);
            if (wanted.contains(mappingKey)) {
                have.add(mappingKey);
            } else {
                companyVendorRepository.delete(mapping);
            }
        }
        companyVendorRepository.flush();
        for (VendorCoverageRequest.Entry entry : request.companies()) {
            Company company = requireCompany(entry.companyId());
            List<UUID> unitIds = entry.orgUnitIds() == null || entry.orgUnitIds().isEmpty() ? java.util.Collections.singletonList(null) : entry.orgUnitIds();
            for (UUID unitId : unitIds) {
                if (!have.contains(key(company.getId(), unitId))) {
                    OrgUnit unit = unitId != null ? orgUnitRepository.findById(unitId).orElseThrow(() -> new OrgUnitNotFoundException(unitId)) : null;
                    companyVendorRepository.save(new CompanyVendor(company, vendor, unit));
                }
            }
        }
        return coverage(vendorId);
    }

    private static String key(UUID companyId, UUID orgUnitId) {
        return companyId + ":" + (orgUnitId != null ? orgUnitId : "all");
    }

    /** Removes one mapping -- a vendor serving the whole company, or one org unit -- leaving its other mappings. */
    @Transactional
    public List<CompanyVendorResponse> unassign(UUID companyId, UUID mappingId) {
        requireCompany(companyId);
        CompanyVendor mapping = companyVendorRepository.findById(mappingId)
                .filter(candidate -> candidate.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new CompanyVendorNotFoundException(mappingId));
        companyVendorRepository.delete(mapping);
        return list(companyId);
    }

    /** Self-service: a Company Admin onboards a vendor for their own franchise -- created with no markup/commission set (platform-controlled), auto-linked to their own roster. */
    @Transactional
    public List<CompanyVendorResponse> createForCompany(UUID companyId, AddCompanyVendorRequest request) {
        vendorService.add(new AddVendorRequest(request.name(), request.contactEmail(), request.contactPhone(), null, null));
        Vendor vendor = vendorRepository.findByContactEmail(request.contactEmail()).orElseThrow();
        return assign(companyId, vendor.getId(), request.orgUnitId());
    }

    private Company requireCompany(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
    }
}
