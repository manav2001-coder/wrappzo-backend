package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.FeatureResponse;
import com.wrapzzo.backend.dto.SetFeatureActiveRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.FeatureService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The feature master -- browse the permission keys, and (platform staff) switch them on or off. */
@RestController
@RequestMapping("/api/platform/features")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureService featureService;

    @GetMapping
    @RequiresPermission(Keys.TEMPLATE_VIEW)
    public List<FeatureResponse> list() {
        return featureService.list();
    }

    @PutMapping("/{featureId}/active")
    @RequiresPermission(Keys.TEMPLATE_EDIT)
    public FeatureResponse setActive(@PathVariable UUID featureId, @Valid @RequestBody SetFeatureActiveRequest request) {
        return featureService.setActive(featureId, request.active());
    }
}
