package com.wrapzzo.backend.service;

import java.time.Duration;
import org.springframework.stereotype.Component;

/** Trims the email outbox: sent emails are kept two weeks (for "did it go out?"), failed ones two months (to be looked into). */
@Component
public class EmailOutboxCleanupJob implements BackgroundJob {

    private final EmailOutboxStore outbox;

    public EmailOutboxCleanupJob(EmailOutboxStore outbox) {
        this.outbox = outbox;
    }

    @Override
    public String name() {
        return "email-outbox-cleanup";
    }

    @Override
    public String label() {
        return "Email outbox cleanup";
    }

    @Override
    public String run() {
        int sent = outbox.deleteSentOlderThan(Duration.ofDays(14));
        int failed = outbox.deleteFailedOlderThan(Duration.ofDays(60));
        return "Removed %d sent and %d failed emails; %d failed emails still kept, %d waiting to send"
                .formatted(sent, failed, outbox.count("FAILED"), outbox.count("PENDING"));
    }
}
