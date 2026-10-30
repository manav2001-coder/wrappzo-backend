package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.DesignationAccessResponse;
import com.wrapzzo.backend.dto.UpdateDesignationAccessRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.DesignationAccessService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Designation-based access grants -- Super Admin, or that company's own Company Admin, via role.manage. */
@RestController
@RequestMapping("/api/platform/companies/{companyId}/designations")
@RequiredArgsConstructor
public class DesignationAccessController {

    private final DesignationAccessService designationAccessService;

    @GetMapping
    @RequiresPermission(Keys.ACCESS_VIEW)
    public List<String> list(@PathVariable UUID companyId) {
        return designationAccessService.listDesignations(companyId);
    }

    @GetMapping("/access")
    @RequiresPermission(Keys.ACCESS_VIEW)
    public DesignationAccessResponse getAccessControls(@PathVariable UUID companyId, @RequestParam String designation) {
        return designationAccessService.getAccessControls(companyId, designation);
    }

    @PutMapping("/access")
    @RequiresPermission(Keys.ACCESS_EDIT)
    public DesignationAccessResponse replaceAccessControls(@PathVariable UUID companyId, @Valid @RequestBody UpdateDesignationAccessRequest request) {
        return designationAccessService.replaceAccessControls(companyId, request.designation(), request.accessControlIds());
    }
}
