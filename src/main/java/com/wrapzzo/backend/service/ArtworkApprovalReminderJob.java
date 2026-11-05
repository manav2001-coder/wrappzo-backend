package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reminds approvers, one email each, of artwork proofs still waiting on them. */
@Component
@RequiredArgsConstructor
public class ArtworkApprovalReminderJob implements BackgroundJob {

    private final ArtworkApprovalReminderService reminders;

    @Override
    public String name() {
        return "artwork-approval-reminders";
    }

    @Override
    public String label() {
        return "Artwork approval reminders";
    }

    @Override
    public String run() {
        return reminders.sendDueReminders();
    }
}
