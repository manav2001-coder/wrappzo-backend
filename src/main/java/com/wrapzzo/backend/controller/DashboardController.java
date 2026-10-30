package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.DashboardDrilldownResponse;
import com.wrapzzo.backend.dto.DashboardResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Company Admin's company-wide overview (PRD 4.7) -- every campaign and Quick Gift request in one place. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@RequiresPermission(Keys.DASHBOARD_VIEW)
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardResponse get() {
        return dashboardService.summary();
    }

    /** The records behind one clicked chart datapoint, e.g. dimension=quickGiftStatus&value=SUBMITTED. */
    @GetMapping("/drilldown")
    public DashboardDrilldownResponse drilldown(@RequestParam String dimension, @RequestParam String value) {
        return dashboardService.drilldown(dimension, value);
    }
}
