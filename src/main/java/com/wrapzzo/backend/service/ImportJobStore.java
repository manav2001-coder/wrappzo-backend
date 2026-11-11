package com.wrapzzo.backend.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The import_jobs table. Claiming a job is one atomic statement ({@code FOR UPDATE SKIP LOCKED}), so with several
 * replicas only one runs a given import; a claim is a lease, and a job whose replica died becomes claimable again when
 * it runs out. The database's clock decides.
 */
@Repository
public class ImportJobStore {

    /** A job a worker has taken, rows included. */
    public record Claimed(UUID id, String kind, UUID companyId, UUID requestedBy, String payload) {
    }

    /** A job as the screen sees it (no rows). */
    public record Row(UUID id, String kind, UUID requestedBy, String status, int totalRows, String result, String errors, String message) {
    }

    private final JdbcTemplate jdbc;

    public ImportJobStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(String kind, UUID companyId, UUID requestedBy, int totalRows, String payload) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO import_jobs (id, kind, company_id, requested_by, status, total_rows, payload, created_at)
                VALUES (?, ?, ?, ?, 'QUEUED', ?, ?, clock_timestamp())
                """, id, kind, companyId, requestedBy, totalRows, payload);
        return id;
    }

    public Optional<Row> find(UUID id) {
        return jdbc.query("SELECT id, kind, requested_by, status, total_rows, result, errors, message FROM import_jobs WHERE id = ?",
                (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getObject("requested_by", UUID.class), rs.getString("status"),
                        rs.getInt("total_rows"), rs.getString("result"), rs.getString("errors"), rs.getString("message")),
                id).stream().findFirst();
    }

    /** Takes the oldest waiting job (or one whose worker died) for {@code lease}, counting this as an attempt. */
    public Optional<Claimed> claimNext(int maxAttempts, Duration lease) {
        // A job whose worker died on its last allowed attempt will not be tried again: say so, and drop its rows.
        jdbc.update("""
                UPDATE import_jobs SET status = 'FAILED', payload = NULL, claimed_until = NULL, finished_at = clock_timestamp(),
                       message = 'The server stopped while this import was running, so nothing was imported. Upload the sheet again.'
                WHERE status = 'RUNNING' AND claimed_until <= clock_timestamp() AND attempts >= ?
                """, maxAttempts);
        List<Claimed> claimed = jdbc.query("""
                UPDATE import_jobs SET status = 'RUNNING', attempts = attempts + 1, started_at = COALESCE(started_at, clock_timestamp()),
                       claimed_until = clock_timestamp() + make_interval(secs => ?)
                WHERE id = (
                    SELECT id FROM import_jobs
                    WHERE status = 'QUEUED' OR (status = 'RUNNING' AND claimed_until <= clock_timestamp() AND attempts < ?)
                    ORDER BY created_at
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED)
                RETURNING id, kind, company_id, requested_by, payload
                """,
                (rs, n) -> new Claimed(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getObject("company_id", UUID.class),
                        rs.getObject("requested_by", UUID.class), rs.getString("payload")),
                lease.toSeconds(), maxAttempts);
        return claimed.stream().findFirst();
    }

    public void markCompleted(UUID id, String resultJson) {
        jdbc.update("UPDATE import_jobs SET status = 'COMPLETED', result = ?, payload = NULL, claimed_until = NULL, finished_at = clock_timestamp() WHERE id = ?",
                resultJson, id);
    }

    public void markFailed(UUID id, String message, String errorsJson) {
        jdbc.update("""
                UPDATE import_jobs SET status = 'FAILED', message = ?, errors = ?, payload = NULL, claimed_until = NULL, finished_at = clock_timestamp()
                WHERE id = ?
                """, truncate(message), errorsJson, id);
    }

    public int deleteFinishedOlderThan(Duration age) {
        return jdbc.update("DELETE FROM import_jobs WHERE status IN ('COMPLETED', 'FAILED') AND finished_at < clock_timestamp() - make_interval(secs => ?)",
                age.toSeconds());
    }

    private static String truncate(String text) {
        if (text == null) return null;
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
