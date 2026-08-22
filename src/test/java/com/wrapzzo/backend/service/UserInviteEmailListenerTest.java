package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A plain unit test, deliberately -- calling the listener bean directly (not via
 * eventPublisher.publishEvent) bypasses Spring's @Async proxy, so this runs synchronously
 * and can assert on emailSender.sent immediately. What it can't cover is the async/
 * after-commit wiring itself (@Async + @TransactionalEventListener), which is standard
 * Spring annotations rather than custom logic -- verified instead by a real bulk-import
 * request completing fast while the invite emails still show up moments later.
 */
class UserInviteEmailListenerTest {

    @Test
    void sendsOneEmailPerPendingInviteWithTheSetupLink() {
        TestEmailSenderConfig.RecordingEmailSender emailSender = new TestEmailSenderConfig.RecordingEmailSender();
        UserInviteEmailListener listener = new UserInviteEmailListener(emailSender);

        List<PendingInvite> invites = List.of(
                new PendingInvite("arpita@nanz-test.com", "Arpita Banerjee", "https://app.wrapzzo.com/setup-password?token=abc", 48),
                new PendingInvite("rajesh@nanz-test.com", "Rajesh Nayak", "https://app.wrapzzo.com/setup-password?token=xyz", 48));

        listener.onUserInvitesIssued(new UserInvitesIssuedEvent(invites));

        assertThat(emailSender.sent).hasSize(2);
        assertThat(emailSender.sent).extracting(TestEmailSenderConfig.RecordingEmailSender.Sent::to)
                .containsExactlyInAnyOrder("arpita@nanz-test.com", "rajesh@nanz-test.com");
        assertThat(emailSender.sent.get(0).html()).contains("setup-password?token=");
        assertThat(emailSender.sent.get(0).subject()).isEqualTo("Set up your Wrapzzo account");
    }

    @Test
    void doesNothingForAnEmptyInviteList() {
        TestEmailSenderConfig.RecordingEmailSender emailSender = new TestEmailSenderConfig.RecordingEmailSender();
        UserInviteEmailListener listener = new UserInviteEmailListener(emailSender);

        listener.onUserInvitesIssued(new UserInvitesIssuedEvent(List.of()));

        assertThat(emailSender.sent).isEmpty();
    }
}
