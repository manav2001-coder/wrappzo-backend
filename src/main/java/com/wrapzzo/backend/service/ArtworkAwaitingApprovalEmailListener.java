package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emails the approver once the upload / approval that made a proof wait on them has committed, off the request thread. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArtworkAwaitingApprovalEmailListener {

    private final ArtworkApprovalReminderService reminders;

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAwaitingApproval(ArtworkAwaitingApprovalEvent event) {
        try {
            reminders.notifyAwaiting(event.designId());
        } catch (RuntimeException e) {
            log.warn("Could not email the approver of artwork {}: {}", event.designId(), e.getMessage());
        }
    }
}
