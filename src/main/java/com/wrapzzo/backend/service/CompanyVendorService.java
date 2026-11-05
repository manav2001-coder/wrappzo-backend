package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.AddCompanyVendorRequest;
import com.wrapzzo.backend.dto.AddVendorRequest;
import com.wrapzzo.backend.dto.CompanyVendorResponse;
import com.wrapzzo.backend.dto.EditCompanyVendorRequest;
import com.wrapzzo.backend.dto.SetVendorCoverageRequest;
import com.wrapzzo.backend.dto.VendorCoverageResponse;
import com.wrapzzo.backend.dto.VendorResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.CompanyVendorNotFoundException;
import com.wrapzzo.backend.exception.InvalidVendorCoverageException;
import com.wrapzzo.backend.exception.OrgUnitNotFoundException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The vendors a company runs. The company admin adds them, maps each to where it serves (the whole company, or chosen
 * divisions and org units -- a unit covers everything beneath it), changes their details and suspends them; platform staff
 * only view (VendorService.list). The roster is also what scopes the company's Gift Store and where an order may be routed
 * (see GiftService.listActiveForUser, OrderService). Every change here is to a vendor the company owns -- another
 * company's vendor looks like it does not exist.
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

    /** Adds a vendor to the company: created, invited by email, and mapped to where it serves, all or nothing. */
    @Transactional
    public List<CompanyVendorResponse> addVendor(UUID companyId, AddCompanyVendorRequest request) {
        Company company = requireCompany(companyId);
        // Checked before the vendor exists: a refused mapping must not leave behind a vendor and an invite email.
        Map<UUID, OrgUnit> units = validatedUnits(companyId, request.name(), request.wholeCompany(), request.orgUnitIds());
        Vendor vendor = vendorService.add(new AddVendorRequest(request.name(), request.contactEmail(), request.contactPhone(), null, null), company);
        applyCoverage(company, vendor, request.wholeCompany(), units);
        return list(companyId);
    }

    /** Sets where one of the company's vendors serves, replacing its earlier mappings here. */
    @Transactional
    public List<CompanyVendorResponse> setCoverage(UUID companyId, UUID vendorId, SetVendorCoverageRequest request) {
        Company company = requireCompany(companyId);
        Vendor vendor = ownedVendor(companyId, vendorId);
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw new InvalidVendorCoverageException(vendor.getName() + " is suspended, so it can't be mapped to anything until it is active again.");
        }
        Map<UUID, OrgUnit> units = validatedUnits(companyId, vendor.getName(), request.wholeCompany(), request.orgUnitIds());
        applyCoverage(company, vendor, request.wholeCompany(), units);
        return list(companyId);
    }

    @Transactional
    public List<CompanyVendorResponse> editVendor(UUID companyId, UUID vendorId, EditCompanyVendorRequest request) {
        vendorService.editDetails(ownedVendor(companyId, vendorId), request.name().trim(), request.contactPhone());
        return list(companyId);
    }

    @Transactional
    public List<CompanyVendorResponse> setVendorStatus(UUID companyId, UUID vendorId, VendorStatus status) {
        vendorService.setStatus(ownedVendor(companyId, vendorId), status);
        return list(companyId);
    }

    @Transactional
    public void resendInvite(UUID companyId, UUID vendorId) {
        vendorService.resendInvite(ownedVendor(companyId, vendorId));
    }

    /** One of this company's own vendors, or "not found" -- never confirming that someone else's exists. */
    private Vendor ownedVendor(UUID companyId, UUID vendorId) {
        return vendorRepository.findById(vendorId)
                .filter(vendor -> vendor.getCompany() != null && vendor.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new VendorNotFoundException(vendorId));
    }

    private Map<UUID, OrgUnit> validatedUnits(UUID companyId, String vendorName, boolean wholeCompany, List<UUID> orgUnitIds) {
        List<UUID> unitIds = orgUnitIds.stream().distinct().toList();
        if (wholeCompany && !unitIds.isEmpty()) {
            throw new InvalidVendorCoverageException("Choose either the whole company or specific divisions and org units, not both.");
        }
        if (!wholeCompany && unitIds.isEmpty()) {
            throw new InvalidVendorCoverageException("Choose at least one division or org unit for " + vendorName + ", or the whole company.");
        }
        Map<UUID, OrgUnit> units = new LinkedHashMap<>();
        for (UUID unitId : unitIds) {
            // The unit must belong to THIS company: a vendor scoped to another company's unit would be invisible here.
            OrgUnit unit = orgUnitRepository.findById(unitId)
                    .filter(candidate -> candidate.getCompany().getId().equals(companyId))
                    .orElseThrow(() -> new OrgUnitNotFoundException(unitId));
            units.put(unitId, unit);
        }
        return units;
    }

    /** Makes the vendor's mappings in this company exactly the whole company, or exactly these units. */
    private void applyCoverage(Company company, Vendor vendor, boolean wholeCompany, Map<UUID, OrgUnit> units) {
        Set<UUID> have = new HashSet<>();
        boolean haveWholeCompany = false;
        for (CompanyVendor mapping : companyVendorRepository.findByVendor_Id(vendor.getId())) {
            if (!mapping.getCompany().getId().equals(company.getId())) {
                continue;
            }
            UUID unitId = mapping.getOrgUnit() != null ? mapping.getOrgUnit().getId() : null;
            boolean keep = unitId == null ? wholeCompany : units.containsKey(unitId);
            if (!keep) {
                companyVendorRepository.delete(mapping);
            } else if (unitId == null) {
                haveWholeCompany = true;
            } else {
                have.add(unitId);
            }
        }
        companyVendorRepository.flush();
        if (wholeCompany && !haveWholeCompany) {
            companyVendorRepository.save(new CompanyVendor(company, vendor, null));
        }
        for (OrgUnit unit : units.values()) {
            if (!have.contains(unit.getId())) {
                companyVendorRepository.save(new CompanyVendor(company, vendor, unit));
            }
        }
    }

    private Company requireCompany(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
    }
}
