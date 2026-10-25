package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.OrgUnitResponse;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.service.OrgUnitService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A company's own org-unit tree, self-service -- e.g. for picking which org unit a self-onboarded vendor should serve. Always scoped to the caller's own company. */
@RestController
@RequestMapping("/api/company/org-units")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('company.vendor.manage')")
public class CompanyOrgUnitController {

    private final OrgUnitService orgUnitService;
    private final UserRepository userRepository;

    @GetMapping
    public List<OrgUnitResponse> list() {
        UUID userId = CurrentUser.id();
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        return orgUnitService.listByCompany(user.getCompany().getId());
    }
}
