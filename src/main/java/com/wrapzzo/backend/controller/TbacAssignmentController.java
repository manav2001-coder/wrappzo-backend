package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AssignTemplatesRequest;
import com.wrapzzo.backend.dto.BulkAssignUserTemplatesResponse;
import com.wrapzzo.backend.dto.BulkUserTemplatesRequest;
import com.wrapzzo.backend.dto.IdListResponse;
import com.wrapzzo.backend.dto.RoleResponse;
import com.wrapzzo.backend.dto.TbacTemplateResponse;
import com.wrapzzo.backend.dto.UserResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.security.TbacAccess;
import com.wrapzzo.backend.service.RoleService;
import com.wrapzzo.backend.service.TemplateService;
import com.wrapzzo.backend.service.UserService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Templates to Role and Templates to User: which templates a role carries, and -- as the exception
 * -- which a person carries on top of their role's. Roles and users are listed here for the
 * assignment screens' pickers; a company user sees their own company's, platform staff pass
 * {@code companyId}.
 */
@RestController
@RequestMapping("/api/tbac")
@RequiredArgsConstructor
public class TbacAssignmentController {

    private final TemplateService templateService;
    private final RoleService roleService;
    private final UserService userService;

    // --- roles ---

    @GetMapping("/roles")
    @RequiresPermission(Keys.TBAC_ROLE_TEMPLATE_MANAGE)
    public List<RoleResponse> roles(@RequestParam(required = false) UUID companyId) {
        return roleService.list(templateService.requireCompanyScope(companyId));
    }

    @GetMapping("/roles/{roleId}")
    @RequiresPermission(Keys.TBAC_ROLE_TEMPLATE_MANAGE)
    public RoleResponse role(@PathVariable UUID roleId) {
        return roleService.get(roleId);
    }

    @GetMapping("/roles/{roleId}/templates")
    @RequiresPermission(Keys.TBAC_ROLE_TEMPLATE_MANAGE)
    public IdListResponse<TbacTemplateResponse> roleTemplates(@PathVariable UUID roleId) {
        return new IdListResponse<>(templateService.roleTemplates(roleId));
    }

    @PostMapping("/roles/{roleId}/templates")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Keys.TBAC_ROLE_TEMPLATE_ASSIGNMENT)
    public void assignRoleTemplates(@PathVariable UUID roleId, @Valid @RequestBody AssignTemplatesRequest request) {
        templateService.assignRoleTemplates(roleId, request.templateIds());
    }

    @DeleteMapping("/roles/{roleId}/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Keys.TBAC_ROLE_TEMPLATE_ASSIGNMENT)
    public void removeRoleTemplate(@PathVariable UUID roleId, @PathVariable UUID templateId) {
        templateService.removeRoleTemplate(roleId, templateId);
    }

    // --- users ---

    @GetMapping("/users")
    @PreAuthorize(TbacAccess.USER_READ)
    public List<UserResponse> users(@RequestParam(required = false) UUID companyId) {
        return userService.listByCompany(templateService.requireCompanyScope(companyId));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize(TbacAccess.USER_READ)
    public UserResponse user(@PathVariable UUID userId) {
        return userService.get(userId);
    }

    @GetMapping("/users/{userId}/templates")
    @PreAuthorize(TbacAccess.USER_READ)
    public IdListResponse<TbacTemplateResponse> userTemplates(@PathVariable UUID userId) {
        return new IdListResponse<>(templateService.userTemplates(userId));
    }

    @PostMapping("/users/{userId}/templates")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Keys.TBAC_USER_TEMPLATE_ASSIGNMENT)
    public void assignUserTemplates(@PathVariable UUID userId, @Valid @RequestBody AssignTemplatesRequest request) {
        templateService.assignUserTemplates(userId, request.templateIds());
    }

    @DeleteMapping("/users/{userId}/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Keys.TBAC_USER_TEMPLATE_ASSIGNMENT)
    public void removeUserTemplate(@PathVariable UUID userId, @PathVariable UUID templateId) {
        templateService.removeUserTemplate(userId, templateId);
    }

    @PostMapping("/users/templates/bulk-assign")
    @RequiresPermission(Keys.TBAC_BULK_ASSIGN_USER_TEMPLATES)
    public BulkAssignUserTemplatesResponse bulkAssign(@Valid @RequestBody BulkUserTemplatesRequest request) {
        return templateService.bulkAssignUserTemplates(request.userIds(), request.templateIds());
    }
}
