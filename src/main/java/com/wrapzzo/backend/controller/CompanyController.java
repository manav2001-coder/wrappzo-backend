package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AttachLogoRequest;
import com.wrapzzo.backend.dto.CompanyResponse;
import com.wrapzzo.backend.dto.CreateCompanyRequest;
import com.wrapzzo.backend.dto.PresignLogoUploadRequest;
import com.wrapzzo.backend.dto.PresignLogoUploadResponse;
import com.wrapzzo.backend.dto.UpdateCompanyRequest;
import com.wrapzzo.backend.model.CompanyStatus;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.CompanyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping("/add")
    @RequiresPermission(Keys.PLATFORM_COMPANY_CREATE)
    public CompanyResponse add(@Valid @RequestBody CreateCompanyRequest request) {
        return companyService.create(request.name());
    }

    @GetMapping("/list")
    @RequiresPermission(Keys.PLATFORM_COMPANY_VIEW)
    public List<CompanyResponse> list() {
        return companyService.list();
    }

    @GetMapping("/view/{id}")
    @RequiresPermission(Keys.PLATFORM_COMPANY_VIEW)
    public CompanyResponse view(@PathVariable UUID id) {
        return companyService.get(id);
    }

    @PostMapping("/edit/{id}")
    @RequiresPermission(Keys.PLATFORM_COMPANY_EDIT)
    public CompanyResponse edit(@PathVariable UUID id, @Valid @RequestBody UpdateCompanyRequest request) {
        return companyService.update(id, request.name());
    }

    @PostMapping("/activate/{id}")
    @RequiresPermission(Keys.PLATFORM_COMPANY_STATUS)
    public CompanyResponse activate(@PathVariable UUID id) {
        return companyService.setStatus(id, CompanyStatus.ACTIVE);
    }

    @PostMapping("/deactivate/{id}")
    @RequiresPermission(Keys.PLATFORM_COMPANY_STATUS)
    public CompanyResponse deactivate(@PathVariable UUID id) {
        return companyService.setStatus(id, CompanyStatus.INACTIVE);
    }

    @PostMapping("/presignLogo/{id}")
    @RequiresPermission(Keys.PLATFORM_COMPANY_EDIT)
    public PresignLogoUploadResponse presignLogo(@PathVariable UUID id, @Valid @RequestBody PresignLogoUploadRequest request) {
        return companyService.presignLogoUpload(id, request.contentType());
    }

    @PostMapping("/attachLogo/{id}")
    @RequiresPermission(Keys.PLATFORM_COMPANY_EDIT)
    public CompanyResponse attachLogo(@PathVariable UUID id, @Valid @RequestBody AttachLogoRequest request) {
        return companyService.attachLogo(id, request.key());
    }
}
