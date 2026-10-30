package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AssignCompanyVendorRequest;
import com.wrapzzo.backend.dto.CompanyVendorResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-side: which vendors serve a company, scoping that company's Gift Store -- gated by company.manage. */
@RestController
@RequestMapping("/api/platform/companies/{companyId}/vendors")
@RequiredArgsConstructor
@RequiresPermission(Keys.PLATFORM_COMPANY_CONFIGURE)
public class CompanyVendorController {

    private final CompanyVendorService companyVendorService;

    @GetMapping
    public List<CompanyVendorResponse> list(@PathVariable UUID companyId) {
        return companyVendorService.list(companyId);
    }

    @PostMapping
    public List<CompanyVendorResponse> assign(@PathVariable UUID companyId, @Valid @RequestBody AssignCompanyVendorRequest request) {
        return companyVendorService.assign(companyId, request.vendorId(), request.orgUnitId());
    }

    /** Removes one mapping (by its id), not every mapping of the vendor: it may serve other org units of the company. */
    @DeleteMapping("/mappings/{mappingId}")
    public List<CompanyVendorResponse> unassign(@PathVariable UUID companyId, @PathVariable UUID mappingId) {
        return companyVendorService.unassign(companyId, mappingId);
    }
}
