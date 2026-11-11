package com.wrapzzo.backend.service;

import org.springframework.stereotype.Service;

/**
 * Saves the email to the outbox instead of sending it. Inside a transaction (a request, or a bulk import)
 * the email is saved with it: if the change rolls back, the email is never sent; if it commits, the email
 * is certain to go out, even if the server restarts first. Outside one it is saved on its own.
 */
@Service
public class OutboxEmailSender implements EmailSender {

    private final EmailOutboxStore outbox;

    public OutboxEmailSender(EmailOutboxStore outbox) {
        this.outbox = outbox;
    }

    @Override
    public void send(String to, String subject, String html) {
        outbox.enqueue(to, subject, html);
    }
}
