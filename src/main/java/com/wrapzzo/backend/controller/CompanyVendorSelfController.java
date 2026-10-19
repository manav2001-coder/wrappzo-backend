package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AddCompanyVendorRequest;
import com.wrapzzo.backend.dto.CompanyVendorResponse;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.service.CompanyVendorService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A company's own self-service vendor onboarding -- gated by company.vendor.manage, always scoped to the caller's own company. */
@RestController
@RequestMapping("/api/company/vendors")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('company.vendor.manage')")
public class CompanyVendorSelfController {

    private final CompanyVendorService companyVendorService;
    private final UserRepository userRepository;

    @GetMapping
    public List<CompanyVendorResponse> list() {
        return companyVendorService.list(currentCompanyId());
    }

    @PostMapping
    public List<CompanyVendorResponse> add(@Valid @RequestBody AddCompanyVendorRequest request) {
        return companyVendorService.createForCompany(currentCompanyId(), request);
    }

    private UUID currentCompanyId() {
        UUID userId = CurrentUser.id();
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        return user.getCompany().getId();
    }
}
