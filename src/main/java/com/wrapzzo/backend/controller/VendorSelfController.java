package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.VendorResponse;
import com.wrapzzo.backend.security.CurrentVendor;
import com.wrapzzo.backend.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A vendor's own self-service view -- gated by ROLE_VENDOR in SecurityConfig, not vendor.manage. */
@RestController
@RequestMapping("/api/vendor")
@RequiredArgsConstructor
public class VendorSelfController {

    private final VendorService vendorService;

    @GetMapping("/view")
    public VendorResponse view() {
        return vendorService.view(CurrentVendor.id());
    }
}
