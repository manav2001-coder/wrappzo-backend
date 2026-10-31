package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.PlatformDashboardResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.PlatformDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Wrappzo staff's platform-wide overview -- the company dashboard stays at /api/dashboard. */
@RestController
@RequestMapping("/api/platform/dashboard")
@RequiredArgsConstructor
@RequiresPermission(Keys.PLATFORM_DASHBOARD_VIEW)
public class PlatformDashboardController {

    private final PlatformDashboardService platformDashboardService;

    @GetMapping
    public PlatformDashboardResponse get() {
        return platformDashboardService.summary();
    }
}
