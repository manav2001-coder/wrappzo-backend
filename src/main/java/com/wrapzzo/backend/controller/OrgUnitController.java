package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.OrgUnitResponse;
import com.wrapzzo.backend.service.OrgUnitService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A company's org-unit tree -- Super Admin only for now, via user.manage (same gate as the users list it accompanies). */
@RestController
@RequestMapping("/api/platform/companies/{companyId}/orgUnits")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('user.manage')")
public class OrgUnitController {

    private final OrgUnitService orgUnitService;

    @GetMapping("/list")
    public List<OrgUnitResponse> list(@PathVariable UUID companyId) {
        return orgUnitService.listByCompany(companyId);
    }
}
