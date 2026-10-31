package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AddFeaturesResponse;
import com.wrapzzo.backend.dto.AddReferencesResponse;
import com.wrapzzo.backend.dto.EffectiveFeaturesResponse;
import com.wrapzzo.backend.dto.FeatureIdsRequest;
import com.wrapzzo.backend.dto.IdListResponse;
import com.wrapzzo.backend.dto.ReferencesRequest;
import com.wrapzzo.backend.dto.TbacTemplateResponse;
import com.wrapzzo.backend.dto.TemplateWriteRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.security.TbacAccess;
import com.wrapzzo.backend.service.TemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Template Master and what a template is made of. The lifecycle (create, edit, deactivate) is the
 * master's; which features a template carries and which templates it includes are the assignment
 * screens', each behind its own {@code .manage} (open the screen) and {@code .assignment} (write)
 * keys, so assignments can be audited without being changed.
 */
@RestController
@RequestMapping("/api/tbac/templates")
@RequiredArgsConstructor
public class TbacTemplateController {

    private final TemplateService templateService;

    @GetMapping
    @PreAuthorize(TbacAccess.TEMPLATE_READ)
    public List<TbacTemplateResponse> list(@RequestParam(required = false) UUID companyId) {
        return templateService.list(companyId);
    }

    @GetMapping("/{templateId}")
    @PreAuthorize(TbacAccess.TEMPLATE_READ)
    public TbacTemplateResponse get(@PathVariable UUID templateId) {
        return templateService.get(templateId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Keys.TBAC_TEMPLATE_MASTER_ADD)
    public TbacTemplateResponse create(@RequestParam(required = false) UUID companyId, @Valid @RequestBody TemplateWriteRequest request) {
        return templateService.create(companyId, request);
    }

    @PutMapping("/{templateId}")
    @RequiresPermission(Keys.TBAC_TEMPLATE_MASTER_EDIT)
    public TbacTemplateResponse update(@PathVariable UUID templateId, @Valid @RequestBody TemplateWriteRequest request) {
        return templateService.update(templateId, request);
    }

    @DeleteMapping("/{templateId}")
    @RequiresPermission(Keys.TBAC_TEMPLATE_MASTER_EDIT)
    public TbacTemplateResponse deactivate(@PathVariable UUID templateId) {
        return templateService.deactivate(templateId);
    }

    @GetMapping("/{templateId}/effective-features")
    @PreAuthorize(TbacAccess.TEMPLATE_READ)
    public EffectiveFeaturesResponse effectiveFeatures(@PathVariable UUID templateId) {
        return templateService.effectiveFeatures(templateId);
    }

    @PostMapping("/{templateId}/features")
    @RequiresPermission(Keys.TBAC_FEATURES_TO_TEMPLATE_ASSIGNMENT)
    public AddFeaturesResponse addFeatures(@PathVariable UUID templateId, @Valid @RequestBody FeatureIdsRequest request) {
        return templateService.addFeatures(templateId, request.featureIds());
    }

    @DeleteMapping("/{templateId}/features/{featureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Keys.TBAC_FEATURES_TO_TEMPLATE_ASSIGNMENT)
    public void removeFeature(@PathVariable UUID templateId, @PathVariable UUID featureId) {
        templateService.removeFeature(templateId, featureId);
    }

    @GetMapping("/{templateId}/references")
    @PreAuthorize(TbacAccess.REFERENCES_READ)
    public IdListResponse<UUID> references(@PathVariable UUID templateId) {
        return new IdListResponse<>(templateService.references(templateId));
    }

    @PostMapping("/{templateId}/references")
    @RequiresPermission(Keys.TBAC_TEMPLATES_TO_TEMPLATE_ASSIGNMENT)
    public AddReferencesResponse addReferences(@PathVariable UUID templateId, @Valid @RequestBody ReferencesRequest request) {
        return templateService.addReferences(templateId, request.referencedTemplateIds());
    }

    @DeleteMapping("/{templateId}/references/{referencedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Keys.TBAC_TEMPLATES_TO_TEMPLATE_ASSIGNMENT)
    public void removeReference(@PathVariable UUID templateId, @PathVariable UUID referencedId) {
        templateService.removeReference(templateId, referencedId);
    }
}
