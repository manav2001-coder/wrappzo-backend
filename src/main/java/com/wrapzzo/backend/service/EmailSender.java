package com.wrapzzo.backend.service;

/**
 * Seam over the actual email provider. {@link ResendEmailSender} is the real
 * implementation; tests substitute a recording double rather than hitting
 * Resend's real API on every run, the same way local dev substitutes MinIO
 * for S3 -- a third-party network dependency, not our own DB/cache.
 */
public interface EmailSender {

    void send(String to, String subject, String html);
}
