package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.JobRunResponse;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.model.JobRun;
import com.wrapzzo.backend.model.JobRunStatus;
import com.wrapzzo.backend.repository.JobRunRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Runs the scheduled jobs and keeps a record of every run, so the Background jobs screen can show
 * what ran, who started it and how it ended -- and run a failed one again. A run that throws is
 * recorded as FAILED with the message only (never a stack trace); it never takes the scheduler down.
 */
@Service
@Slf4j
public class BackgroundJobRunner {

    static final String SCHEDULE = "Schedule";
    private static final int LIST_LIMIT = 500;
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final int MAX_TEXT = 1000;

    private final JobRunRepository runs;
    private final UserRepository users;
    private final Map<String, BackgroundJob> jobsByName;

    public BackgroundJobRunner(JobRunRepository runs, UserRepository users, List<BackgroundJob> jobs) {
        this.runs = runs;
        this.users = users;
        this.jobsByName = jobs.stream().collect(Collectors.toMap(BackgroundJob::name, Function.identity()));
    }

    /** The scheduler's entry point: run the job and record how it went. */
    public void runScheduled(BackgroundJob job) {
        execute(runs.save(new JobRun(job.name(), SCHEDULE)), job);
    }

    public List<JobRunResponse> list() {
        return runs.findAllByOrderByStartedAtDesc(PageRequest.of(0, LIST_LIMIT)).stream().map(this::toResponse).toList();
    }

    /** Runs a failed run's job again, on the same record: the attempt count goes up. */
    public JobRunResponse retry(UUID runId) {
        JobRun run = runs.findById(runId).orElseThrow(() -> new ConflictException("That run no longer exists"));
        if (run.getStatus() != JobRunStatus.FAILED) {
            throw new ConflictException("Only a failed run can be run again");
        }
        BackgroundJob job = jobsByName.get(run.getJobName());
        if (job == null) {
            throw new ConflictException("The job \"" + run.getJobName() + "\" no longer exists");
        }
        run.setStatus(JobRunStatus.RUNNING);
        run.setTriggeredBy(currentUserName());
        run.setAttempts(run.getAttempts() + 1);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(null);
        run.setResult(null);
        run.setError(null);
        return toResponse(execute(runs.save(run), job));
    }

    /** Clears a finished run from the screen. A run in progress stays. */
    public void remove(UUID runId) {
        JobRun run = runs.findById(runId).orElseThrow(() -> new ConflictException("That run no longer exists"));
        if (run.getStatus() == JobRunStatus.RUNNING) {
            throw new ConflictException("A run in progress can't be removed");
        }
        runs.delete(run);
    }

    private JobRun execute(JobRun run, BackgroundJob job) {
        // A job may set and clear its own authentication (it acts as another user); give the
        // caller's back afterwards, so a retry from the screen does not log its own request out.
        Authentication callers = SecurityContextHolder.getContext().getAuthentication();
        try {
            run.setResult(truncate(job.run()));
            run.setStatus(JobRunStatus.COMPLETED);
        } catch (RuntimeException e) {
            log.warn("Background job {} failed: {}", job.name(), e.getMessage());
            run.setError(truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            run.setStatus(JobRunStatus.FAILED);
        } finally {
            SecurityContext restored = SecurityContextHolder.createEmptyContext();
            restored.setAuthentication(callers);
            SecurityContextHolder.setContext(restored);
        }
        run.setFinishedAt(Instant.now());
        JobRun saved = runs.save(run);
        runs.deleteOlderThan(job.name(), Instant.now().minus(RETENTION));
        return saved;
    }

    private String currentUserName() {
        return users.findById(CurrentUser.id()).map(user -> user.getName()).orElse(SCHEDULE);
    }

    private JobRunResponse toResponse(JobRun run) {
        BackgroundJob job = jobsByName.get(run.getJobName());
        return JobRunResponse.from(run, job != null ? job.label() : run.getJobName());
    }

    private static String truncate(String text) {
        if (text == null) return null;
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }
}
