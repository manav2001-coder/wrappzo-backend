package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reminds approvers, one email each, of orders still waiting on their approval. */
@Component
@RequiredArgsConstructor
public class OrderApprovalReminderJob implements BackgroundJob {

    private final OrderApprovalNotificationService notifications;

    @Override
    public String name() {
        return "order-approval-reminders";
    }

    @Override
    public String label() {
        return "Order approval reminders";
    }

    @Override
    public String run() {
        return notifications.sendDueReminders();
    }
}
