package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AddVendorRequest;
import com.wrapzzo.backend.dto.AddVendorResponse;
import com.wrapzzo.backend.dto.EditVendorRequest;
import com.wrapzzo.backend.dto.VendorResponse;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.service.VendorService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Super Admin only, via vendor.manage -- onboarding/managing vendors is exclusively a platform action per the PRD. */
@RestController
@RequestMapping("/api/platform/vendors")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('vendor.manage')")
public class VendorController {

    private final VendorService vendorService;

    @PostMapping("/add")
    public AddVendorResponse add(@Valid @RequestBody AddVendorRequest request) {
        return vendorService.add(request);
    }

    @GetMapping("/list")
    public List<VendorResponse> list() {
        return vendorService.list();
    }

    @GetMapping("/view/{id}")
    public VendorResponse view(@PathVariable UUID id) {
        return vendorService.view(id);
    }

    @PostMapping("/edit/{id}")
    public VendorResponse edit(@PathVariable UUID id, @Valid @RequestBody EditVendorRequest request) {
        return vendorService.edit(id, request);
    }

    @PostMapping("/suspend/{id}")
    public VendorResponse suspend(@PathVariable UUID id) {
        return vendorService.setStatus(id, VendorStatus.SUSPENDED);
    }

    @PostMapping("/reactivate/{id}")
    public VendorResponse reactivate(@PathVariable UUID id) {
        return vendorService.setStatus(id, VendorStatus.ACTIVE);
    }
}
