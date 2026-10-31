package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.MenuLabelsRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.CompanyMenuLabelService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform config > Menu config: platform staff naming a company's navigation menus. The company's own users read them through /api/auth/me. */
@RestController
@RequestMapping("/api/platform/companies/{companyId}/menu-labels")
@RequiredArgsConstructor
public class CompanyMenuLabelController {

    private final CompanyMenuLabelService companyMenuLabelService;

    @GetMapping
    @RequiresPermission(Keys.PLATFORM_MENU_CONFIG_VIEW)
    public Map<String, String> list(@PathVariable UUID companyId) {
        return companyMenuLabelService.list(companyId);
    }

    @PutMapping
    @RequiresPermission(Keys.PLATFORM_MENU_CONFIG_EDIT)
    public Map<String, String> replace(@PathVariable UUID companyId, @Valid @RequestBody MenuLabelsRequest request) {
        return companyMenuLabelService.replace(companyId, request.labels());
    }
}
