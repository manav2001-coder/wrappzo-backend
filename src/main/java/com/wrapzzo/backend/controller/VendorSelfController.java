package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.VendorDivisionResponse;
import com.wrapzzo.backend.dto.VendorResponse;
import java.util.List;
import com.wrapzzo.backend.service.VendorDivisionService;
import com.wrapzzo.backend.security.CurrentVendor;
import com.wrapzzo.backend.security.VendorEndpoint;
import com.wrapzzo.backend.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A vendor's own self-service view -- gated by ROLE_VENDOR in SecurityConfig, not vendor.manage. */
@RestController
@RequestMapping("/api/vendor")
@RequiredArgsConstructor
@VendorEndpoint
public class VendorSelfController {

    private final VendorService vendorService;
    private final VendorDivisionService vendorDivisionService;

    @GetMapping("/view")
    public VendorResponse view() {
        return vendorService.view(CurrentVendor.id());
    }

    /** The divisions this vendor is mapped to serve -- what the portal's division filter offers. */
    @GetMapping("/divisions")
    public List<VendorDivisionResponse> divisions() {
        return vendorDivisionService.servedDivisions(CurrentVendor.id());
    }
}
