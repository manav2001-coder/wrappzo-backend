package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs after the bulk-import transaction actually commits (TransactionPhase.AFTER_COMMIT) --
 * never send an invite for a user row that ended up not persisting -- and off the request
 * thread (@Async), so a large import's HTTP response doesn't wait on hundreds/thousands of
 * sequential Resend calls. See AsyncConfig for the executor this runs on.
 */
@Component
@RequiredArgsConstructor
public class UserInviteEmailListener {

    private final EmailSender emailSender;

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserInvitesIssued(UserInvitesIssuedEvent event) {
        for (PendingInvite invite : event.invites()) {
            emailSender.send(
                    invite.email(),
                    "Set up your Wrappzo account",
                    """
                    <p>Hi %s,</p>
                    <p>You've been added on Wrappzo. Set up your password to get started:</p>
                    <p><a href="%s">Set up your password</a></p>
                    <p>This link expires in %d hours.</p>
                    """
                            .formatted(invite.name(), invite.setupUrl(), invite.expiryHours()));
        }
    }
}
