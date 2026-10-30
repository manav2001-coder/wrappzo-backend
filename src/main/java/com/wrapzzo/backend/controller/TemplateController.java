package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AddReferencesResponse;
import com.wrapzzo.backend.dto.BulkUserTemplatesRequest;
import com.wrapzzo.backend.dto.BulkUserTemplatesResponse;
import com.wrapzzo.backend.dto.CloneTemplateRequest;
import com.wrapzzo.backend.dto.TemplateIdsAddRequest;
import com.wrapzzo.backend.dto.TemplateIdsRequest;
import com.wrapzzo.backend.dto.TemplateRequest;
import com.wrapzzo.backend.dto.TemplateResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.TemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access templates. A company manages its own (and sees the system masters) under its companies
 * path; platform staff manage the system masters under /system; edits and removals of either go
 * by template id, where the service decides who may touch which.
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/companies/{companyId}/templates")
    @RequiresPermission(Keys.TEMPLATE_VIEW)
    public List<TemplateResponse> list(@PathVariable UUID companyId) {
        return templateService.listForCompany(companyId);
    }

    @PostMapping("/companies/{companyId}/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Keys.TEMPLATE_EDIT)
    public TemplateResponse create(@PathVariable UUID companyId, @Valid @RequestBody TemplateRequest request) {
        return templateService.create(companyId, request);
    }

    @PostMapping("/companies/{companyId}/templates/{templateId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Keys.TEMPLATE_EDIT)
    public TemplateResponse clone(@PathVariable UUID companyId, @PathVariable UUID templateId, @Valid @RequestBody CloneTemplateRequest request) {
        return templateService.clone(companyId, templateId, request.name());
    }

    @GetMapping("/system-templates")
    @RequiresPermission(Keys.TEMPLATE_VIEW)
    public List<TemplateResponse> listSystem() {
        return templateService.listSystem();
    }

    @PostMapping("/system-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Keys.TEMPLATE_EDIT)
    public TemplateResponse createSystem(@Valid @RequestBody TemplateRequest request) {
        return templateService.create(null, request);
    }

    @PutMapping("/templates/{templateId}")
    @RequiresPermission(Keys.TEMPLATE_EDIT)
    public TemplateResponse update(@PathVariable UUID templateId, @Valid @RequestBody TemplateRequest request) {
        return templateService.update(templateId, request);
    }

    @DeleteMapping("/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Keys.TEMPLATE_EDIT)
    public void delete(@PathVariable UUID templateId) {
        templateService.delete(templateId);
    }

    @GetMapping("/companies/{companyId}/users/{userId}/templates")
    @RequiresPermission(Keys.ACCESS_VIEW)
    public Set<UUID> userTemplates(@PathVariable UUID companyId, @PathVariable UUID userId) {
        return templateService.userTemplateIds(companyId, userId);
    }

    @PutMapping("/companies/{companyId}/users/{userId}/templates")
    @RequiresPermission(Keys.ACCESS_EDIT)
    public Set<UUID> replaceUserTemplates(@PathVariable UUID companyId, @PathVariable UUID userId, @Valid @RequestBody TemplateIdsRequest request) {
        return templateService.replaceUserTemplates(companyId, userId, request.templateIds());
    }

    @PostMapping("/templates/{templateId}/references")
    @RequiresPermission(Keys.TEMPLATE_EDIT)
    public AddReferencesResponse addReferences(@PathVariable UUID templateId, @Valid @RequestBody TemplateIdsAddRequest request) {
        return templateService.addReferences(templateId, request.templateIds());
    }

    @DeleteMapping("/templates/{templateId}/references/{referencedId}")
    @RequiresPermission(Keys.TEMPLATE_EDIT)
    public TemplateResponse removeReference(@PathVariable UUID templateId, @PathVariable UUID referencedId) {
        return templateService.removeReference(templateId, referencedId);
    }

    @PostMapping("/companies/{companyId}/users/templates/bulk-assign")
    @RequiresPermission(Keys.ACCESS_EDIT)
    public BulkUserTemplatesResponse bulkAssign(@PathVariable UUID companyId, @Valid @RequestBody BulkUserTemplatesRequest request) {
        return templateService.bulkAssignUserTemplates(companyId, request.userIds(), request.templateIds());
    }
}
