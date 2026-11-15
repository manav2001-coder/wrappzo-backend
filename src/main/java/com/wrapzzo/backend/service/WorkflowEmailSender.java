package com.wrapzzo.backend.service;

import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * How the order workflow sends its emails: approvers told something waits for them, reminders, order decisions, and the vendor's
 * new-order and proof emails. It is the one place the platform-wide switch (Platform config > Email settings) is checked, so a workflow
 * email can never slip past it and a new one gets it for free -- by using this, not {@link EmailSender} (a test enforces that).
 *
 * <p>Switched off, the email is dropped, not held back: nothing is sent later for an event that happened while it was off. (Reminders
 * are the exception -- the daily jobs ask {@link #enabled()} first and leave their proofs and orders due.) Account invites, password
 * setup and the doctor photo request are not workflow emails and keep using {@link EmailSender}, which is never switched off.
 */
@Component
public class WorkflowEmailSender {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEmailSender.class);

    private final EmailSender delegate;
    private final BooleanSupplier enabled;

    @Autowired
    public WorkflowEmailSender(EmailSender delegate, PlatformSettings settings) {
        this(delegate, settings::workflowEmailsEnabled);
    }

    WorkflowEmailSender(EmailSender delegate, BooleanSupplier enabled) {
        this.delegate = delegate;
        this.enabled = enabled;
    }

    /** Always sends -- for tests that are not about the switch. */
    public static WorkflowEmailSender unconditional(EmailSender delegate) {
        return new WorkflowEmailSender(delegate, () -> true);
    }

    public boolean enabled() {
        return enabled.getAsBoolean();
    }

    public void send(String to, String subject, String html) {
        if (!enabled()) {
            log.debug("Workflow emails are switched off: not sending \"{}\" to {}", subject, to);
            return;
        }
        delegate.send(to, subject, html);
    }
}
