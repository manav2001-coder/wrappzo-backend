package com.wrapzzo.backend.service;

/**
 * Seam over the actual email provider ({@link ResendEmailSender}); the outbox dispatcher is the only caller.
 * Tests substitute a double rather than hitting Resend's real API, the same way local dev substitutes MinIO for S3.
 */
public interface EmailTransport {

    /** Sends the email now; throws {@link com.wrapzzo.backend.exception.EmailDeliveryException} if the provider did not accept it. */
    void send(String to, String subject, String html);
}
