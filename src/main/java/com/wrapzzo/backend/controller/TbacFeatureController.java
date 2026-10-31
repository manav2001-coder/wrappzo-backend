package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.BulkRegisterFeaturesRequest;
import com.wrapzzo.backend.dto.BulkRegisterFeaturesResponse;
import com.wrapzzo.backend.dto.SetActiveRequest;
import com.wrapzzo.backend.dto.TbacCatalogueEntry;
import com.wrapzzo.backend.dto.TbacFeatureResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.security.TbacAccess;
import com.wrapzzo.backend.service.FeatureService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature Master -- the permission keys a company has registered. A company user works on their own
 * company; platform staff pass {@code companyId}. There is no edit and no delete: a key is what the
 * route guards match on, so the only change to a feature is switching it on or off.
 */
@RestController
@RequestMapping("/api/tbac/features")
@RequiredArgsConstructor
public class TbacFeatureController {

    private final FeatureService featureService;

    @GetMapping
    @PreAuthorize(TbacAccess.FEATURE_READ)
    public List<TbacFeatureResponse> list(@RequestParam(required = false) UUID companyId) {
        return featureService.list(companyId);
    }

    @GetMapping("/catalogue")
    @RequiresPermission(Keys.TBAC_FEATURE_MASTER_ADD)
    public List<TbacCatalogueEntry> catalogue() {
        return featureService.catalogue();
    }

    @PostMapping("/bulk-register")
    @RequiresPermission(Keys.TBAC_FEATURE_MASTER_ADD)
    public BulkRegisterFeaturesResponse bulkRegister(
            @RequestParam(required = false) UUID companyId, @Valid @RequestBody BulkRegisterFeaturesRequest request) {
        return featureService.register(companyId, request.features().stream().map(BulkRegisterFeaturesRequest.Entry::key).toList());
    }

    @PutMapping("/{featureId}")
    @RequiresPermission(Keys.TBAC_FEATURE_MASTER_EDIT)
    public TbacFeatureResponse setActive(
            @RequestParam(required = false) UUID companyId, @PathVariable UUID featureId, @Valid @RequestBody SetActiveRequest request) {
        return featureService.setActive(companyId, featureId, request.active());
    }
}
