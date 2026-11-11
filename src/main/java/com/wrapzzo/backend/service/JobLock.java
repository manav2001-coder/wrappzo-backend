package com.wrapzzo.backend.service;

import java.lang.management.ManagementFactory;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lets only one replica run a scheduled job at a time. Every replica fires the same cron at the same
 * moment; the first to take the job's row runs it and the others skip it, so a reminder email is sent
 * once, not once per replica.
 *
 * The lock is a row in {@code job_locks} taken with a single atomic statement, and the database's own
 * clock decides whether it is free. It is held for {@link #MAX_HOLD_MINUTES} at most, so a replica that
 * dies mid-job does not block the job forever, and for {@link #MIN_HOLD_MINUTES} at least after it
 * starts, so a replica whose clock fires a few seconds late does not run the job a second time.
 */
@Component
@Slf4j
public class JobLock {

    static final int MAX_HOLD_MINUTES = 30;
    static final int MIN_HOLD_MINUTES = 5;

    private final JdbcTemplate jdbc;
    private final String owner = ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID();

    public JobLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True when this replica now holds the job; false when another replica has it. */
    public boolean tryAcquire(String jobName) {
        int taken = jdbc.update("""
                INSERT INTO job_locks (name, locked_at, locked_until, locked_by)
                VALUES (?, clock_timestamp(), clock_timestamp() + make_interval(mins => ?), ?)
                ON CONFLICT (name) DO UPDATE
                SET locked_at = EXCLUDED.locked_at, locked_until = EXCLUDED.locked_until, locked_by = EXCLUDED.locked_by
                WHERE job_locks.locked_until <= clock_timestamp()
                """, jobName, MAX_HOLD_MINUTES, owner);
        return taken == 1;
    }

    /** Frees the job once it has finished, but not before the minimum hold has passed. */
    public void release(String jobName) {
        try {
            jdbc.update("""
                    UPDATE job_locks
                    SET locked_until = GREATEST(clock_timestamp(), locked_at + make_interval(mins => ?))
                    WHERE name = ? AND locked_by = ?
                    """, MIN_HOLD_MINUTES, jobName, owner);
        } catch (RuntimeException e) {
            // Not fatal: the lock simply expires on its own at the maximum hold.
            log.warn("Could not release the lock on job {}: {}", jobName, e.getMessage());
        }
    }
}
