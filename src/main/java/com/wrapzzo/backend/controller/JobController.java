package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.JobRunResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.BackgroundJobRunner;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Platform config > Background jobs: platform staff only (the keys are never grantable to a company). */
@RestController
@RequestMapping("/api/platform/jobs")
@RequiredArgsConstructor
public class JobController {

    private final BackgroundJobRunner runner;

    @GetMapping
    @RequiresPermission(Keys.PLATFORM_JOBS_VIEW)
    public List<JobRunResponse> list() {
        return runner.list();
    }

    @PostMapping("/{runId}/retry")
    @RequiresPermission(Keys.PLATFORM_JOBS_MANAGE)
    public JobRunResponse retry(@PathVariable UUID runId) {
        return runner.retry(runId);
    }

    @DeleteMapping("/{runId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Keys.PLATFORM_JOBS_MANAGE)
    public void remove(@PathVariable UUID runId) {
        runner.remove(runId);
    }
}
