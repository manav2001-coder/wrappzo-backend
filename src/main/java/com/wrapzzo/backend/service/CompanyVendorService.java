package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.AddCompanyVendorRequest;
import com.wrapzzo.backend.dto.AddVendorRequest;
import com.wrapzzo.backend.dto.CompanyVendorResponse;
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
