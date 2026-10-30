package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AccessControlResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.AccessControlCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The catalogue of permissions a company designation can be given -- read-only, for the access grid. */
@RestController
@RequestMapping("/api/access-controls")
@RequiredArgsConstructor
@RequiresPermission(Keys.ACCESS_VIEW)
public class AccessControlController {

    private final AccessControlCatalogService accessControlCatalogService;

    @GetMapping
    public List<AccessControlResponse> list() {
        return accessControlCatalogService.listCompanyAssignable();
    }
}
