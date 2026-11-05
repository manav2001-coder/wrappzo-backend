package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AddCompanyVendorRequest;
import com.wrapzzo.backend.dto.AvailableVendorResponse;
import com.wrapzzo.backend.dto.SetVendorCoverageRequest;
import com.wrapzzo.backend.dto.CompanyVendorResponse;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.CompanyVendorService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A company's own self-service vendor onboarding -- gated by company.vendor.manage, always scoped to the caller's own company. */
@RestController
@RequestMapping("/api/company/vendors")
@RequiredArgsConstructor
public class CompanyVendorSelfController {

    private final CompanyVendorService companyVendorService;
    private final UserRepository userRepository;

    @GetMapping
    @RequiresPermission(Keys.COMPANY_VENDOR_VIEW)
    public List<CompanyVendorResponse> list() {
        return companyVendorService.list(currentCompanyId());
    }

    @PostMapping
    @RequiresPermission(Keys.COMPANY_VENDOR_ADD)
    public List<CompanyVendorResponse> add(@Valid @RequestBody AddCompanyVendorRequest request) {
        return companyVendorService.createForCompany(currentCompanyId(), request);
    }

    @GetMapping("/available")
    @RequiresPermission(Keys.COMPANY_VENDOR_ASSIGN)
    public List<AvailableVendorResponse> available() {
        return companyVendorService.availableVendors();
    }

    @PutMapping("/{vendorId}/coverage")
    @RequiresPermission(Keys.COMPANY_VENDOR_ASSIGN)
    public List<CompanyVendorResponse> setCoverage(@PathVariable UUID vendorId, @Valid @RequestBody SetVendorCoverageRequest request) {
        return companyVendorService.setCoverage(currentCompanyId(), vendorId, request);
    }

    @DeleteMapping("/{vendorId}")
    @RequiresPermission(Keys.COMPANY_VENDOR_REMOVE)
    public List<CompanyVendorResponse> remove(@PathVariable UUID vendorId) {
        return companyVendorService.removeVendor(currentCompanyId(), vendorId);
    }

    private UUID currentCompanyId() {
        UUID userId = CurrentUser.id();
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        return user.getCompany().getId();
    }
}
