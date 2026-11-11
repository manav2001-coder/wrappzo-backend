package com.wrapzzo.backend.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The email outbox table. Enqueueing joins the caller's transaction when there is one, so an email exists
 * only if the change that caused it committed. Claiming is one atomic statement that takes rows with
 * {@code FOR UPDATE SKIP LOCKED}, so replicas never take the same email; a claim is a lease, and an email
 * whose replica died mid-send becomes claimable again when the lease runs out. The database's clock decides.
 */
@Repository
public class EmailOutboxStore {

    /** An email a dispatcher has taken to send. */
    public record Claimed(UUID id, String to, String subject, String html, int attempts) {
    }

    private final JdbcTemplate jdbc;

    public EmailOutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(String to, String subject, String html) {
        jdbc.update("""
                INSERT INTO email_outbox (id, to_address, subject, html, status, attempts, next_attempt_at, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, clock_timestamp(), clock_timestamp())
                """, UUID.randomUUID(), to, subject, html);
    }

    /** Takes up to {@code limit} due emails for {@code lease}, counting this as an attempt. */
    public List<Claimed> claimDue(int limit, int maxAttempts, Duration lease) {
        // An email whose lease ran out on its last allowed attempt will never be tried again: say so.
        jdbc.update("""
                UPDATE email_outbox SET status = 'FAILED', claimed_until = NULL,
                       last_error = COALESCE(last_error, 'The sender stopped before this email was confirmed sent')
                WHERE status = 'SENDING' AND claimed_until <= clock_timestamp() AND attempts >= ?
                """, maxAttempts);
        return jdbc.query("""
                UPDATE email_outbox SET status = 'SENDING', attempts = attempts + 1,
                       claimed_until = clock_timestamp() + make_interval(secs => ?)
                WHERE id IN (
                    SELECT id FROM email_outbox
                    WHERE (status = 'PENDING' AND next_attempt_at <= clock_timestamp())
                       OR (status = 'SENDING' AND claimed_until <= clock_timestamp() AND attempts < ?)
                    ORDER BY next_attempt_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
                RETURNING id, to_address, subject, html, attempts
                """,
                (rs, row) -> new Claimed(rs.getObject("id", UUID.class), rs.getString("to_address"), rs.getString("subject"),
                        rs.getString("html"), rs.getInt("attempts")),
                lease.toSeconds(), maxAttempts, limit);
    }

    public void markSent(UUID id) {
        jdbc.update("UPDATE email_outbox SET status = 'SENT', sent_at = clock_timestamp(), claimed_until = NULL, last_error = NULL WHERE id = ?", id);
    }

    /** Back to the queue, to be tried again after {@code delay}. */
    public void retryLater(UUID id, Duration delay, String error) {
        jdbc.update("""
                UPDATE email_outbox SET status = 'PENDING', claimed_until = NULL, last_error = ?,
                       next_attempt_at = clock_timestamp() + make_interval(secs => ?)
                WHERE id = ?
                """, truncate(error), delay.toSeconds(), id);
    }

    public void markFailed(UUID id, String error) {
        jdbc.update("UPDATE email_outbox SET status = 'FAILED', claimed_until = NULL, last_error = ? WHERE id = ?", truncate(error), id);
    }

    public int deleteSentOlderThan(Duration age) {
        return jdbc.update("DELETE FROM email_outbox WHERE status = 'SENT' AND created_at < clock_timestamp() - make_interval(secs => ?)", age.toSeconds());
    }

    public int deleteFailedOlderThan(Duration age) {
        return jdbc.update("DELETE FROM email_outbox WHERE status = 'FAILED' AND created_at < clock_timestamp() - make_interval(secs => ?)", age.toSeconds());
    }

    public int count(String status) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE status = ?", Integer.class, status);
        return n == null ? 0 : n;
    }

    private static String truncate(String text) {
        if (text == null) return null;
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
