package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.BulkImportUsersRequest;
import com.wrapzzo.backend.dto.BulkImportUsersResponse;
import com.wrapzzo.backend.dto.SetExtraOrgUnitsRequest;
import com.wrapzzo.backend.dto.SetCompanyWideRequest;
import com.wrapzzo.backend.dto.UserResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Company user onboarding -- Super Admin only for now, via user.manage. */
@RestController
@RequestMapping("/api/platform/companies/{companyId}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/bulkImport")
    @RequiresPermission(Keys.USER_CREATE)
    public BulkImportUsersResponse bulkImport(@PathVariable UUID companyId, @Valid @RequestBody BulkImportUsersRequest request) {
        return userService.bulkImport(companyId, request.rows());
    }

    @GetMapping("/list")
    @RequiresPermission(Keys.USER_VIEW)
    public List<UserResponse> list(@PathVariable UUID companyId) {
        return userService.listByCompany(companyId);
    }

    @PostMapping("/{userId}/makeCompanyAdmin")
    @RequiresPermission(Keys.USER_EDIT)
    public UserResponse makeCompanyAdmin(@PathVariable UUID companyId, @PathVariable UUID userId) {
        return userService.makeCompanyAdmin(companyId, userId);
    }

    @PostMapping("/{userId}/removeCompanyAdmin")
    @RequiresPermission(Keys.USER_EDIT)
    public UserResponse removeCompanyAdmin(@PathVariable UUID companyId, @PathVariable UUID userId) {
        return userService.removeCompanyAdmin(companyId, userId);
    }

    /** The org units a user covers beyond their own (replace-all) -- a manager over several zones. */
    @PutMapping("/{userId}/companyWide")
    @RequiresPermission(Keys.USER_EDIT)
    public UserResponse setCompanyWide(@PathVariable UUID companyId, @PathVariable UUID userId, @Valid @RequestBody SetCompanyWideRequest request) {
        return userService.setCompanyWide(companyId, userId, request.companyWide());
    }

    @PutMapping("/{userId}/extraOrgUnits")
    @RequiresPermission(Keys.USER_EDIT)
    public UserResponse setExtraOrgUnits(@PathVariable UUID companyId, @PathVariable UUID userId, @Valid @RequestBody SetExtraOrgUnitsRequest request) {
        return userService.setExtraOrgUnits(companyId, userId, request.orgUnitIds());
    }

    @DeleteMapping("/{userId}")
    @RequiresPermission(Keys.USER_DELETE)
    public void delete(@PathVariable UUID companyId, @PathVariable UUID userId) {
        userService.deleteUser(companyId, userId);
    }
}
