package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.FeatureResponse;
import com.wrapzzo.backend.dto.RegisterFeaturesRequest;
import com.wrapzzo.backend.dto.SetFeatureActiveRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.FeatureService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The feature master -- per company: what it has registered and switched on. */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureService featureService;

    @GetMapping("/companies/{companyId}/features")
    @RequiresPermission(Keys.TEMPLATE_VIEW)
    public List<FeatureResponse> listForCompany(@PathVariable UUID companyId) {
        return featureService.listForCompany(companyId);
    }

    @PostMapping("/companies/{companyId}/features/register")
    @RequiresPermission(Keys.TEMPLATE_EDIT)
    public List<FeatureResponse> register(@PathVariable UUID companyId, @RequestBody(required = false) RegisterFeaturesRequest request) {
        return featureService.register(companyId, request == null ? null : request.featureIds());
    }

    @PutMapping("/companies/{companyId}/features/{featureId}/active")
    @RequiresPermission(Keys.TEMPLATE_EDIT)
    public FeatureResponse setActive(@PathVariable UUID companyId, @PathVariable UUID featureId, @Valid @RequestBody SetFeatureActiveRequest request) {
        return featureService.setActive(companyId, featureId, request.active());
    }

    /** The whole catalogue (platform staff: every key, for system templates; a company: the keys it can hold). */
    @GetMapping("/features")
    @RequiresPermission(Keys.TEMPLATE_VIEW)
    public List<FeatureResponse> catalogue() {
        return featureService.catalogue();
    }
}
