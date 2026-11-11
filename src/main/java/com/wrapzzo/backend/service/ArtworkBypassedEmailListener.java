package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emails the people whose approval was skipped, once the bypass has committed, off the request thread. A failed email never fails the approval. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArtworkBypassedEmailListener {

    private final ArtworkBypassNotificationService notifications;

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBypassed(ArtworkBypassedEvent event) {
        try {
            notifications.notifySkipped(event);
        } catch (RuntimeException e) {
            log.warn("Could not tell the people skipped on artwork {}: {}", event.designId(), e.getMessage());
        }
    }
}
