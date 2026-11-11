package com.wrapzzo.backend.service;

import com.wrapzzo.backend.exception.EmailDeliveryException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends what is in the email outbox. Polls every few seconds; each replica takes its own share of the due
 * emails, so more replicas simply send faster. A failed send is tried again later -- after 1 minute, 5, 15,
 * an hour, three hours -- and after six attempts the email is marked FAILED with the reason, and kept for a
 * while so it can be looked at. Delivery is at-least-once: if a replica dies after Resend accepted an email
 * but before it was marked sent, the email is sent again when its claim runs out.
 */
@Component
@ConditionalOnProperty(name = "email.outbox.dispatch-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class EmailOutboxDispatcher {

    static final int MAX_ATTEMPTS = 6;
    /** How long after attempt N fails (index N-1) the next one happens. */
    static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(3));
    /** Longer than any one send can take, so a live replica never has its email taken from it. */
    private static final Duration CLAIM = Duration.ofMinutes(2);

    private final EmailOutboxStore outbox;
    private final EmailTransport transport;
    private final int batchSize;

    public EmailOutboxDispatcher(EmailOutboxStore outbox, EmailTransport transport, @Value("${email.outbox.batch-size:10}") int batchSize) {
        this.outbox = outbox;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${email.outbox.poll-ms:3000}", initialDelayString = "${email.outbox.initial-delay-ms:10000}")
    public void poll() {
        try {
            // Keep going while there is a full batch waiting, so a big backlog drains without waiting a poll between batches.
            while (dispatchOnce() == batchSize) {
                // next batch
            }
        } catch (RuntimeException e) {
            // Never let one bad poll stop the schedule.
            log.warn("The email outbox poll failed: {}", e.getMessage());
        }
    }

    /** Sends one batch of due emails; returns how many it took. */
    public int dispatchOnce() {
        List<EmailOutboxStore.Claimed> batch = outbox.claimDue(batchSize, MAX_ATTEMPTS, CLAIM);
        for (EmailOutboxStore.Claimed email : batch) {
            try {
                transport.send(email.to(), email.subject(), email.html());
                outbox.markSent(email.id());
            } catch (RuntimeException e) {
                String reason = e instanceof EmailDeliveryException || e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (email.attempts() >= MAX_ATTEMPTS) {
                    log.warn("Giving up on the email to {} after {} attempts: {}", email.to(), email.attempts(), reason);
                    outbox.markFailed(email.id(), reason);
                } else {
                    outbox.retryLater(email.id(), BACKOFF.get(email.attempts() - 1), reason);
                }
            }
        }
        return batch.size();
    }
}
