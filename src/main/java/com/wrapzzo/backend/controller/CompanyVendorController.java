package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.CompanyVendorResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.CompanyVendorService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-side, read-only: which vendors serve a company. The company runs its own vendors (CompanyVendorSelfController). */
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
}
