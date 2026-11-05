package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.VendorCoverageResponse;
import com.wrapzzo.backend.dto.VendorResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.CompanyVendorService;
import com.wrapzzo.backend.service.VendorService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform staff see every vendor across all franchises -- and nothing more. Vendors are added, mapped, edited and suspended
 * by the company that owns them (CompanyVendorSelfController).
 */
@RestController
@RequestMapping("/api/platform/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;
    private final CompanyVendorService companyVendorService;

    @GetMapping("/list")
    @RequiresPermission(Keys.PLATFORM_VENDOR_VIEW)
    public List<VendorResponse> list(@RequestParam(required = false) UUID companyId) {
        return vendorService.list(companyId);
    }

    @GetMapping("/{id}/coverage")
    @RequiresPermission(Keys.PLATFORM_VENDOR_VIEW)
    public VendorCoverageResponse coverage(@PathVariable UUID id) {
        return companyVendorService.coverage(id);
    }

    @GetMapping("/view/{id}")
    @RequiresPermission(Keys.PLATFORM_VENDOR_VIEW)
    public VendorResponse view(@PathVariable UUID id) {
        return vendorService.view(id);
    }
}
