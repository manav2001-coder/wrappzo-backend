package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.AddCompanyVendorRequest;
import com.wrapzzo.backend.dto.AddVendorRequest;
import com.wrapzzo.backend.dto.CompanyVendorResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which vendors serve a company -- this roster is what scopes that company's Gift Store /
 * Quick Gift catalog (see GiftBrowseController, V31). list()/assign()/unassign() are the
 * admin-only (company.manage) path, operating on any company; createForCompany() is the
 * narrower self-service path (company.vendor.manage) a Company Admin uses for their own.
 */
@Service
@RequiredArgsConstructor
public class CompanyVendorService {

    private final CompanyVendorRepository companyVendorRepository;
    private final CompanyRepository companyRepository;
    private final VendorRepository vendorRepository;
    private final VendorService vendorService;

    @Transactional(readOnly = true)
    public List<CompanyVendorResponse> list(UUID companyId) {
        requireCompany(companyId);
        return companyVendorRepository.findByCompany_IdOrderByCreatedAtDesc(companyId).stream()
                .map(CompanyVendorResponse::from)
                .toList();
    }

    @Transactional
    public List<CompanyVendorResponse> assign(UUID companyId, UUID vendorId) {
        Company company = requireCompany(companyId);
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow(() -> new VendorNotFoundException(vendorId));
        try {
            companyVendorRepository.save(new CompanyVendor(company, vendor));
        } catch (DataIntegrityViolationException e) {
            // Already assigned -- idempotent, not an error.
        }
        return list(companyId);
    }

    @Transactional
    public List<CompanyVendorResponse> unassign(UUID companyId, UUID vendorId) {
        requireCompany(companyId);
        companyVendorRepository.deleteByCompany_IdAndVendor_Id(companyId, vendorId);
        return list(companyId);
    }

    /** Self-service: a Company Admin onboards a vendor for their own franchise -- created with no markup/commission set (platform-controlled), auto-linked to their own roster. */
    @Transactional
    public List<CompanyVendorResponse> createForCompany(UUID companyId, AddCompanyVendorRequest request) {
        vendorService.add(new AddVendorRequest(request.name(), request.contactEmail(), request.contactPhone(), null, null));
        Vendor vendor = vendorRepository.findByContactEmail(request.contactEmail()).orElseThrow();
        return assign(companyId, vendor.getId());
    }

    private Company requireCompany(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
    }
}
