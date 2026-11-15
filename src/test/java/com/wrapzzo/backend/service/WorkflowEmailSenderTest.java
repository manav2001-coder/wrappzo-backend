package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** The one place the Super Admin's email switch is checked: on it sends, off it drops, and the reminder jobs stand down. */
class WorkflowEmailSenderTest {

    private final TestEmailSenderConfig.RecordingEmailSender mail = new TestEmailSenderConfig.RecordingEmailSender();
    private final AtomicBoolean on = new AtomicBoolean(true);
    private final WorkflowEmailSender workflow = new WorkflowEmailSender(mail, on::get);

    @Test
    void sendsWhileOnAndDropsWhileOff() {
        workflow.send("a@x.com", "One", "<p>1</p>");
        on.set(false);
        workflow.send("b@x.com", "Two", "<p>2</p>");
        on.set(true);
        workflow.send("c@x.com", "Three", "<p>3</p>");

        assertThat(mail.sent).extracting(TestEmailSenderConfig.RecordingEmailSender.Sent::subject).containsExactly("One", "Three");
    }

    @Test
    void followsTheSwitchLive() {
        assertThat(workflow.enabled()).isTrue();
        on.set(false);
        assertThat(workflow.enabled()).isFalse();
    }

    @Test
    void theOrderApprovalReminderJobStandsDownWhenOffAndTouchesNothing() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderApprovalService approvals = mock(OrderApprovalService.class);
        on.set(false);
        String result = new OrderApprovalNotificationService(orders, approvals, workflow, "https://app.example").sendDueReminders();

        assertThat(result).contains("paused");
        assertThat(mail.sent).isEmpty();
        verifyNoInteractions(orders, approvals);
    }

    @Test
    void theArtworkReminderJobStandsDownWhenOffAndTouchesNothing() {
        OrderDesignRepository designs = mock(OrderDesignRepository.class);
        OrderDesignService designService = mock(OrderDesignService.class);
        on.set(false);
        String result = new ArtworkApprovalReminderService(designs, designService, workflow, "https://app.example").sendDueReminders();

        assertThat(result).contains("paused");
        assertThat(mail.sent).isEmpty();
        verifyNoInteractions(designs, designService);
    }

    @Test
    void theVendorEmailsAreDroppedWhenOff() {
        OrderRepository orders = mock(OrderRepository.class);
        VendorRepository vendors = mock(VendorRepository.class);
        on.set(false);
        // The service still does its lookups (nothing found here), but whatever it would send goes nowhere.
        new VendorNotificationService(orders, mock(OrderDesignRepository.class), vendors, workflow, "https://app.example")
                .orderAssigned(UUID.randomUUID(), UUID.randomUUID(), null);
        assertThat(mail.sent).isEmpty();
    }
}
