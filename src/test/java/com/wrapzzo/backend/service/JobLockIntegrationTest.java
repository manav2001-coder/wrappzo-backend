package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Two replicas, one database: only one of them may run a job at a time. Not @Transactional -- the lock must really commit. */
@SpringBootTest
class JobLockIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private JobLock replicaA;

    private final String job = "lock-test-" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM job_locks WHERE name = ?", job);
    }

    /** A second replica is a second JobLock with its own identity, sharing the database. */
    private JobLock replicaB() {
        return new JobLock(jdbc);
    }

    @Test
    void onlyOneReplicaTakesAJobAtATime() {
        JobLock replicaB = replicaB();

        assertThat(replicaA.tryAcquire(job)).isTrue();
        assertThat(replicaB.tryAcquire(job)).isFalse();
        assertThat(replicaA.tryAcquire(job)).isFalse();
    }

    @Test
    void aFinishedJobStaysLockedForTheMinimumHoldSoALateReplicaDoesNotRunItAgain() {
        JobLock replicaB = replicaB();
        assertThat(replicaA.tryAcquire(job)).isTrue();

        replicaA.release(job);

        assertThat(replicaB.tryAcquire(job)).isFalse();
    }

    @Test
    void aJobIsFreeAgainOnceItsLockHasRunOut() {
        JobLock replicaB = replicaB();
        assertThat(replicaA.tryAcquire(job)).isTrue();
        jdbc.update("UPDATE job_locks SET locked_at = locked_at - interval '1 hour', locked_until = locked_until - interval '1 hour' WHERE name = ?", job);

        assertThat(replicaB.tryAcquire(job)).isTrue();
    }

    @Test
    void aReplicaThatDiedMidJobDoesNotBlockItForever() {
        JobLock replicaB = replicaB();
        assertThat(replicaA.tryAcquire(job)).isTrue();
        // Nobody releases it: the lock's maximum hold passes.
        jdbc.update("UPDATE job_locks SET locked_until = clock_timestamp() - interval '1 second' WHERE name = ?", job);

        assertThat(replicaB.tryAcquire(job)).isTrue();
    }

    @Test
    void aReplicaCannotReleaseAJobAnotherReplicaHolds() {
        JobLock replicaB = replicaB();
        assertThat(replicaA.tryAcquire(job)).isTrue();
        jdbc.update("UPDATE job_locks SET locked_until = clock_timestamp() - interval '1 second' WHERE name = ?", job);
        assertThat(replicaB.tryAcquire(job)).isTrue();

        replicaA.release(job);

        // B still holds it: A's release changed nothing.
        assertThat(replicaA.tryAcquire(job)).isFalse();
    }

    @Test
    void ofManyReplicasFiringAtTheSameMomentExactlyOneTakesTheJob() throws Exception {
        int replicas = 8;
        ExecutorService pool = Executors.newFixedThreadPool(replicas);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < replicas; i++) {
                JobLock replica = replicaB();
                Callable<Boolean> fire = () -> {
                    start.await();
                    return replica.tryAcquire(job);
                };
                results.add(pool.submit(fire));
            }
            start.countDown();
            int winners = 0;
            for (Future<Boolean> result : results) if (result.get()) winners++;
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void differentJobsDoNotBlockEachOther() {
        String other = job + "-other";
        try {
            assertThat(replicaA.tryAcquire(job)).isTrue();
            assertThat(replicaB().tryAcquire(other)).isTrue();
        } finally {
            jdbc.update("DELETE FROM job_locks WHERE name = ?", other);
        }
    }
}
