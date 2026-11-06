package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emails the approver when an order lands on them and the requester when it is decided -- after commit, off the request thread. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderApprovalEmailListener {

    private final OrderApprovalNotificationService notifications;

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAwaiting(OrderAwaitingApprovalEvent event) {
        try {
            notifications.notifyAwaiting(event.orderId());
        } catch (RuntimeException e) {
            log.warn("Could not email the approver of order {}: {}", event.orderId(), e.getMessage());
        }
    }

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(OrderApprovalDecidedEvent event) {
        try {
            notifications.notifyDecided(event.orderId());
        } catch (RuntimeException e) {
            log.warn("Could not email the requester of order {}: {}", event.orderId(), e.getMessage());
        }
    }
}
