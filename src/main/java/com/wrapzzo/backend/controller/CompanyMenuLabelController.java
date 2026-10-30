package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.MenuLabelsRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.CompanyMenuLabelService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A company renaming its own navigation menus. Everyone reads them through /api/auth/me; this is the editor's side. */
@RestController
@RequestMapping("/api/company/menu-labels")
@RequiredArgsConstructor
@RequiresPermission(Keys.MENU_EDIT)
public class CompanyMenuLabelController {

    private final CompanyMenuLabelService companyMenuLabelService;

    @GetMapping
    public Map<String, String> list() {
        return companyMenuLabelService.listOwn();
    }

    @PutMapping
    public Map<String, String> replace(@Valid @RequestBody MenuLabelsRequest request) {
        return companyMenuLabelService.replace(request.labels());
    }
}
