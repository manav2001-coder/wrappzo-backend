package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.RoleRequest;
import com.wrapzzo.backend.dto.RoleResponse;
import com.wrapzzo.backend.dto.TemplateIdsRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
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

/** The roles master -- Super Admin, or that company's own Company Admin. */
@RestController
@RequestMapping("/api/platform/companies/{companyId}/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @RequiresPermission(Keys.ROLE_VIEW)
    public List<RoleResponse> list(@PathVariable UUID companyId) {
        return roleService.list(companyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Keys.ROLE_EDIT)
    public RoleResponse create(@PathVariable UUID companyId, @Valid @RequestBody RoleRequest request) {
        return roleService.create(companyId, request);
    }

    @PutMapping("/{roleId}")
    @RequiresPermission(Keys.ROLE_EDIT)
    public RoleResponse update(@PathVariable UUID companyId, @PathVariable UUID roleId, @Valid @RequestBody RoleRequest request) {
        return roleService.update(companyId, roleId, request);
    }

    @PutMapping("/{roleId}/templates")
    @RequiresPermission(Keys.ROLE_EDIT)
    public RoleResponse replaceTemplates(@PathVariable UUID companyId, @PathVariable UUID roleId, @Valid @RequestBody TemplateIdsRequest request) {
        return roleService.replaceTemplates(companyId, roleId, request.templateIds());
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Keys.ROLE_EDIT)
    public void delete(@PathVariable UUID companyId, @PathVariable UUID roleId) {
        roleService.delete(companyId, roleId);
    }
}
