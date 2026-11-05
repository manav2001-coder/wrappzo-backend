package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The reminder rules: a first email on arrival, then one a day for at most three days, one email per person. */
class ArtworkApprovalReminderServiceTest {

    private final OrderDesignRepository designs = mock(OrderDesignRepository.class);
    private final OrderDesignService orderDesignService = mock(OrderDesignService.class);
    private final TestEmailSenderConfig.RecordingEmailSender email = new TestEmailSenderConfig.RecordingEmailSender();
    private final ArtworkApprovalReminderService service =
            new ArtworkApprovalReminderService(designs, orderDesignService, email, "https://app.example");

    private final User asha = user("Asha", "asha@example.com");

    @Test
    void emailsTheApproverWhenAProofStartsWaitingWithoutNamingTheVendor() {
        OrderDesign design = design(1, 5, Instant.now());
        when(designs.findById(design.getId())).thenReturn(Optional.of(design));
        when(orderDesignService.awaitedApprovers(design)).thenReturn(List.of(asha));

        service.notifyAwaiting(design.getId());

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).to()).isEqualTo("asha@example.com");
        assertThat(email.sent.get(0).subject()).contains("v1 for ORD-5");
        assertThat(email.sent.get(0).html()).contains("https://app.example/approvals").doesNotContain("Sky Prints");
    }

    @Test
    void remindsOnceADayHasPassedAndCountsIt() {
        OrderDesign design = design(1, 5, Instant.now().minus(Duration.ofHours(25)));
        when(designs.findByStatusInAndRemindersSentLessThanOrderByAwaitingSinceAsc(any(), anyInt())).thenReturn(List.of(design));
        when(orderDesignService.awaitedApprovers(design)).thenReturn(List.of(asha));

        service.sendDueReminders();

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).subject()).startsWith("Reminder:");
        assertThat(design.getRemindersSent()).isEqualTo(1);
        assertThat(design.getLastReminderAt()).isNotNull();
    }

    @Test
    void doesNotRemindBeforeADayHasPassed() {
        OrderDesign fresh = design(1, 5, Instant.now().minus(Duration.ofHours(3)));
        OrderDesign reminded = design(1, 6, Instant.now().minus(Duration.ofHours(30)));
        reminded.setLastReminderAt(Instant.now().minus(Duration.ofHours(2)));
        when(designs.findByStatusInAndRemindersSentLessThanOrderByAwaitingSinceAsc(any(), anyInt())).thenReturn(List.of(fresh, reminded));
        when(orderDesignService.awaitedApprovers(any())).thenReturn(List.of(asha));

        service.sendDueReminders();

        assertThat(email.sent).isEmpty();
    }

    @Test
    void sendsOneEmailListingEveryProofWaitingOnTheSamePerson() {
        OrderDesign a = design(1, 5, Instant.now().minus(Duration.ofHours(26)));
        OrderDesign b = design(2, 6, Instant.now().minus(Duration.ofHours(48)));
        when(designs.findByStatusInAndRemindersSentLessThanOrderByAwaitingSinceAsc(any(), anyInt())).thenReturn(List.of(a, b));
        when(orderDesignService.awaitedApprovers(any())).thenReturn(List.of(asha));

        service.sendDueReminders();

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).subject()).isEqualTo("Reminder: 2 artwork proofs are waiting for your approval");
        assertThat(email.sent.get(0).html()).contains("ORD-5").contains("ORD-6");
        assertThat(a.getRemindersSent()).isEqualTo(1);
        assertThat(b.getRemindersSent()).isEqualTo(1);
    }

    private static User user(String name, String emailAddress) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getName()).thenReturn(name);
        when(user.getEmail()).thenReturn(emailAddress);
        return user;
    }

    private static OrderDesign design(int version, int orderNo, Instant awaitingSince) {
        Gift gift = mock(Gift.class);
        when(gift.getName()).thenReturn("Pen set");
        Order order = mock(Order.class);
        when(order.getOrderNo()).thenReturn(orderNo);
        when(order.getGift()).thenReturn(gift);
        when(order.getRecipientName()).thenReturn("Dr Rao");
        Vendor vendor = mock(Vendor.class);
        when(vendor.getName()).thenReturn("Sky Prints");
        OrderDesign design = new OrderDesign(order, version, "k", "f.pdf", vendor);
        design.setId(UUID.randomUUID());
        design.setStatus(OrderDesignStatus.PENDING_FINAL_APPROVAL);
        design.startAwaiting(awaitingSince);
        return design;
    }
}
