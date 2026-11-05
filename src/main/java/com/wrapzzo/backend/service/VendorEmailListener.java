package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emails a vendor once what concerns them has committed, off the request thread; a failed email never fails the action. */
@Component
@RequiredArgsConstructor
@Slf4j
public class VendorEmailListener {

    private final VendorNotificationService notifications;

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssigned(VendorOrderAssignedEvent event) {
        try {
            notifications.orderAssigned(event.orderId(), event.vendorId(), event.previousVendorId());
        } catch (RuntimeException e) {
            log.warn("Could not email the vendor about order {}: {}", event.orderId(), e.getMessage());
        }
    }

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProofDecided(VendorProofDecidedEvent event) {
        try {
            notifications.proofDecided(event.designId(), event.approved());
        } catch (RuntimeException e) {
            log.warn("Could not email the vendor about proof {}: {}", event.designId(), e.getMessage());
        }
    }
}
