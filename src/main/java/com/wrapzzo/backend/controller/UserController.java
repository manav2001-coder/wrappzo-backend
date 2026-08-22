package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.BulkImportUsersRequest;
import com.wrapzzo.backend.dto.BulkImportUsersResponse;
import com.wrapzzo.backend.dto.UserResponse;
import com.wrapzzo.backend.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Company user onboarding -- Super Admin only for now, via user.manage. */
@RestController
@RequestMapping("/api/platform/companies/{companyId}/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('user.manage')")
public class UserController {

    private final UserService userService;

    @PostMapping("/bulkImport")
    public BulkImportUsersResponse bulkImport(@PathVariable UUID companyId, @Valid @RequestBody BulkImportUsersRequest request) {
        return userService.bulkImport(companyId, request.rows());
    }

    @GetMapping("/list")
    public List<UserResponse> list(@PathVariable UUID companyId) {
        return userService.listByCompany(companyId);
    }
}
