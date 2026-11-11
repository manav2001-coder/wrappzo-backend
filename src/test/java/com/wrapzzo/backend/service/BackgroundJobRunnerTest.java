package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.dto.JobRunResponse;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.model.JobRun;
import com.wrapzzo.backend.model.JobRunStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.JobRunRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class BackgroundJobRunnerTest {

    private final JobRunRepository runs = mock(JobRunRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final JobLock lock = mock(JobLock.class);
    private final AtomicBoolean fail = new AtomicBoolean(false);

    private final BackgroundJob job = new BackgroundJob() {
        public String name() { return "demo"; }
        public String label() { return "Demo job"; }
        public String run() {
            if (fail.get()) throw new IllegalStateException("database is down");
            return "did the work";
        }
    };

    private BackgroundJobRunner runner;

    @BeforeEach
    void setUp() {
        runner = new BackgroundJobRunner(runs, users, lock, List.of(job));
        when(lock.tryAcquire("demo")).thenReturn(true);
        when(runs.save(any(JobRun.class))).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aScheduledRunIsRecordedWithItsResult() {
        runner.runScheduled(job);

        org.mockito.ArgumentCaptor<JobRun> saved = org.mockito.ArgumentCaptor.forClass(JobRun.class);
        verify(runs, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        JobRun last = saved.getValue();
        assertThat(last.getStatus()).isEqualTo(JobRunStatus.COMPLETED);
        assertThat(last.getTriggeredBy()).isEqualTo("Schedule");
        assertThat(last.getResult()).isEqualTo("did the work");
        assertThat(last.getFinishedAt()).isNotNull();
    }

    @Test
    void aReplicaThatDoesNotGetTheLockDoesNotRunTheJobOrRecordARun() {
        when(lock.tryAcquire("demo")).thenReturn(false);

        runner.runScheduled(job);

        verify(runs, never()).save(any(JobRun.class));
        verify(lock, never()).release(any());
    }

    @Test
    void theLockIsReleasedAfterTheJobEvenWhenItFails() {
        fail.set(true);

        runner.runScheduled(job);

        verify(lock).release("demo");
    }

    @Test
    void aFailingJobIsRecordedAsFailedWithTheMessageAndDoesNotThrow() {
        fail.set(true);

        runner.runScheduled(job);

        org.mockito.ArgumentCaptor<JobRun> saved = org.mockito.ArgumentCaptor.forClass(JobRun.class);
        verify(runs, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(JobRunStatus.FAILED);
        assertThat(saved.getValue().getError()).isEqualTo("database is down");
    }

    @Test
    void retryingAFailedRunRunsItAgainOnTheSameRecordAndCountsTheAttempt() {
        JobRun failed = new JobRun("demo", "Schedule");
        failed.setStatus(JobRunStatus.FAILED);
        failed.setError("database is down");
        UUID admin = UUID.randomUUID();
        User user = new User();
        user.setName("Manav");
        when(runs.findById(any())).thenReturn(Optional.of(failed));
        when(users.findById(admin)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        JobRunResponse response = runner.retry(UUID.randomUUID());

        assertThat(response.status()).isEqualTo(JobRunStatus.COMPLETED);
        assertThat(response.attempts()).isEqualTo(2);
        assertThat(response.triggeredBy()).isEqualTo("Manav");
        assertThat(response.error()).isNull();
        assertThat(response.label()).isEqualTo("Demo job");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void onlyAFailedRunCanBeRetriedAndOnlyAFinishedOneRemoved() {
        JobRun done = new JobRun("demo", "Schedule");
        done.setStatus(JobRunStatus.COMPLETED);
        JobRun running = new JobRun("demo", "Schedule");
        when(runs.findById(any())).thenReturn(Optional.of(done));
        assertThatThrownBy(() -> runner.retry(UUID.randomUUID())).isInstanceOf(ConflictException.class);

        when(runs.findById(any())).thenReturn(Optional.of(running));
        assertThatThrownBy(() -> runner.remove(UUID.randomUUID())).isInstanceOf(ConflictException.class);
        verify(runs, never()).delete(any(JobRun.class));

        when(runs.findById(any())).thenReturn(Optional.of(done));
        runner.remove(UUID.randomUUID());
        verify(runs).delete(done);
    }
}
