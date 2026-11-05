package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** When each background job runs. Every run goes through the runner, so it is recorded. */
@Component
@RequiredArgsConstructor
public class BackgroundJobScheduler {

    private final BackgroundJobRunner runner;
    private final RefreshTokenCleanupJob refreshTokenCleanup;
    private final VendorRefreshTokenCleanupJob vendorRefreshTokenCleanup;
    private final RecurringOrderJob recurringOrders;
    private final ArtworkApprovalReminderJob artworkApprovalReminders;

    @Scheduled(cron = "0 0 3 * * *")
    public void refreshTokenCleanup() {
        runner.runScheduled(refreshTokenCleanup);
    }

    @Scheduled(cron = "0 5 3 * * *")
    public void vendorRefreshTokenCleanup() {
        runner.runScheduled(vendorRefreshTokenCleanup);
    }

    @Scheduled(cron = "0 15 6 * * *")
    public void recurringOrders() {
        runner.runScheduled(recurringOrders);
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void artworkApprovalReminders() {
        runner.runScheduled(artworkApprovalReminders);
    }
}
