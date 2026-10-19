package com.wrapzzo.backend.service;

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
 * Which vendors serve a company (admin-only, company.manage) -- this roster is what scopes
 * that company's Gift Store / Quick Gift catalog (see GiftBrowseController, V31).
 */
@Service
@RequiredArgsConstructor
public class CompanyVendorService {

    private final CompanyVendorRepository companyVendorRepository;
    private final CompanyRepository companyRepository;
    private final VendorRepository vendorRepository;

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

    private Company requireCompany(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
    }
}
