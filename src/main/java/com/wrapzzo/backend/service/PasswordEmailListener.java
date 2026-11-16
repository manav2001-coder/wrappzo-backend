package com.wrapzzo.backend.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The two emails of "forgot password": the link, and the notice that the password changed. Sent through the plain email sender,
 * never the workflow one, so the Super Admin's switch for order emails cannot stop a person from getting back into their account.
 */
@Component
@RequiredArgsConstructor
public class PasswordEmailListener {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    private final EmailSender emailSender;

    @Async("emailTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResetRequested(PasswordResetRequestedEvent event) {
        emailSender.send(event.email(), "Reset your Wrappzo password", resetBody(event));
    }

    @Async("emailTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordChanged(PasswordChangedEvent event) {
        emailSender.send(event.email(), "Your Wrappzo password was changed", changedBody(event));
    }

    static String resetBody(PasswordResetRequestedEvent event) {
        return """
                <p>Hi %s,</p>
                <p>We got a request to reset your Wrappzo password. Tap the link to choose a new one:</p>
                <p><a href="%s">Reset your password</a></p>
                <p>The link works once, and expires in %d minutes.</p>
                <p>If you did not ask for this, you can ignore this email: your password stays the same.</p>
                """
                .formatted(event.name(), event.resetUrl(), event.expiryMinutes());
    }

    static String changedBody(PasswordChangedEvent event) {
        return """
                <p>Hi %s,</p>
                <p>Your Wrappzo password was changed on %s (India time). You were signed out of your other devices.</p>
                <p>If this was you, there is nothing more to do. If it was not, tell your admin straight away, and write to support@wrappzo.com.</p>
                """
                .formatted(event.name(), WHEN.format(event.changedAt()));
    }
}
