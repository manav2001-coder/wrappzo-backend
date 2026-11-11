package com.wrapzzo.backend.service;

import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the bulk uploads that were queued. Polls every couple of seconds and runs one import at a time on this replica;
 * other replicas take other imports. The claim lasts 30 minutes, so an import that is simply slow is not taken by a second
 * replica, while one whose replica died is picked up again -- twice at most, then it is marked failed.
 */
@Component
@ConditionalOnProperty(name = "import.dispatch-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ImportJobDispatcher {

    static final int MAX_ATTEMPTS = 3;
    private static final Duration CLAIM = Duration.ofMinutes(30);

    private final ImportJobStore jobs;
    private final ImportJobService service;

    public ImportJobDispatcher(ImportJobStore jobs, ImportJobService service) {
        this.jobs = jobs;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${import.poll-ms:2000}", initialDelayString = "${import.initial-delay-ms:10000}")
    public void poll() {
        try {
            while (runNext()) {
                // keep going while there is something waiting
            }
        } catch (RuntimeException e) {
            log.warn("The import poll failed: {}", e.getMessage());
        }
    }

    /** Runs the next waiting import; false when nothing was waiting. */
    public boolean runNext() {
        Optional<ImportJobStore.Claimed> job = jobs.claimNext(MAX_ATTEMPTS, CLAIM);
        job.ifPresent(service::execute);
        return job.isPresent();
    }
}
