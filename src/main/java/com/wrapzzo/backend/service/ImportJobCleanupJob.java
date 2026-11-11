package com.wrapzzo.backend.service;

import java.time.Duration;
import org.springframework.stereotype.Component;

/** Clears finished bulk-upload records after a week -- long enough to look back at what an upload did. */
@Component
public class ImportJobCleanupJob implements BackgroundJob {

    private final ImportJobStore jobs;

    public ImportJobCleanupJob(ImportJobStore jobs) {
        this.jobs = jobs;
    }

    @Override
    public String name() {
        return "import-job-cleanup";
    }

    @Override
    public String label() {
        return "Bulk upload cleanup";
    }

    @Override
    public String run() {
        return "Removed " + jobs.deleteFinishedOlderThan(Duration.ofDays(7)) + " finished bulk uploads";
    }
}
