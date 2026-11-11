package com.wrapzzo.backend.service;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the bulk uploads that were queued. Polls every couple of seconds and runs up to {@code import.workers} (3) imports at a time on this replica;
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
    /** Imports run side by side, up to {@code import.workers} at a time on this replica: one sheet at a time made a rush of uploads queue for minutes. */
    private final ExecutorService workers;
    private final Semaphore slots;

    ImportJobDispatcher(ImportJobStore jobs, ImportJobService service) {
        this(jobs, service, 1);
    }

    @Autowired
    public ImportJobDispatcher(ImportJobStore jobs, ImportJobService service, @Value("${import.workers:3}") int workerCount) {
        this.jobs = jobs;
        this.service = service;
        int count = Math.max(1, workerCount);
        this.slots = new Semaphore(count);
        this.workers = Executors.newFixedThreadPool(count, runnable -> {
            Thread thread = new Thread(runnable, "import-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Scheduled(fixedDelayString = "${import.poll-ms:2000}", initialDelayString = "${import.initial-delay-ms:10000}")
    public void poll() {
        try {
            // Start a worker for each free slot while something is waiting; a worker keeps going until nothing is.
            while (slots.tryAcquire()) {
                Optional<ImportJobStore.Claimed> job = jobs.claimNext(MAX_ATTEMPTS, CLAIM);
                if (job.isEmpty()) {
                    slots.release();
                    return;
                }
                workers.execute(() -> work(job.get()));
            }
        } catch (RuntimeException e) {
            log.warn("The import poll failed: {}", e.getMessage());
        }
    }

    private void work(ImportJobStore.Claimed first) {
        try {
            Optional<ImportJobStore.Claimed> next = Optional.of(first);
            while (next.isPresent()) {
                service.execute(next.get());
                next = jobs.claimNext(MAX_ATTEMPTS, CLAIM);
            }
        } catch (RuntimeException e) {
            log.warn("An import worker stopped: {}", e.getMessage());
        } finally {
            slots.release();
        }
    }

    /** Runs the next waiting import on the calling thread; false when nothing was waiting. */
    public boolean runNext() {
        Optional<ImportJobStore.Claimed> job = jobs.claimNext(MAX_ATTEMPTS, CLAIM);
        job.ifPresent(service::execute);
        return job.isPresent();
    }

    @PreDestroy
    void stop() {
        workers.shutdown();
    }
}
