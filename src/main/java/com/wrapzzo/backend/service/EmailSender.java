package com.wrapzzo.backend.service;

/**
 * How the app sends an email. {@link OutboxEmailSender} is the real implementation: it saves the email to
 * the outbox and returns, and the {@link EmailOutboxDispatcher} sends it afterwards, with retries. So a
 * call here never fails because the email provider is down. Tests substitute a recording double.
 */
public interface EmailSender {

    void send(String to, String subject, String html);
}
