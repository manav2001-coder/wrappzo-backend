package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderApprovalStatus;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.OrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The order reminder rules: a day (or another day) since the last email, one email per approver, and it counts. */
class OrderApprovalReminderTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderApprovalService approvals = mock(OrderApprovalService.class);
    private final TestEmailSenderConfig.RecordingEmailSender email = new TestEmailSenderConfig.RecordingEmailSender();
    private final OrderApprovalNotificationService service = new OrderApprovalNotificationService(orders, approvals, email, "https://app.example");

    private final User rm = user("Ravi RM", "ravi@example.com");
    private final User mr = user("Mira MR", "mira@example.com");

    @Test
    void remindsTheApproverOnceForEverythingThatHasWaitedADayAndCountsIt() {
        Order first = waiting(Instant.now().minus(Duration.ofHours(30)), 0, null);
        Order second = waiting(Instant.now().minus(Duration.ofHours(26)), 1, Instant.now().minus(Duration.ofHours(25)));
        when(orders.findByApprovalStatusAndApprovalRemindersSentLessThanOrderByCreatedAtAsc(any(), anyInt())).thenReturn(List.of(first, second));
        when(approvals.awaitedApprovers(any())).thenReturn(List.of(rm));

        service.sendDueReminders();

        assertThat(email.sent).hasSize(1);
        assertThat(email.sent.get(0).to()).isEqualTo("ravi@example.com");
        assertThat(email.sent.get(0).subject()).isEqualTo("Reminder: 2 orders are waiting for your approval");
        assertThat(first.getApprovalRemindersSent()).isEqualTo(1);
        assertThat(second.getApprovalRemindersSent()).isEqualTo(2);
        assertThat(first.getApprovalLastReminderAt()).isNotNull();
    }

    @Test
    void leavesAlonePendingOrdersThatWereRemindedOrPlacedWithinTheDay() {
        Order fresh = waiting(Instant.now().minus(Duration.ofHours(3)), 0, null);
        Order justReminded = waiting(Instant.now().minus(Duration.ofHours(50)), 1, Instant.now().minus(Duration.ofHours(2)));
        when(orders.findByApprovalStatusAndApprovalRemindersSentLessThanOrderByCreatedAtAsc(any(), anyInt())).thenReturn(List.of(fresh, justReminded));
        when(approvals.awaitedApprovers(any())).thenReturn(List.of(rm));

        service.sendDueReminders();

        assertThat(email.sent).isEmpty();
        assertThat(fresh.getApprovalRemindersSent()).isZero();
        assertThat(justReminded.getApprovalRemindersSent()).isEqualTo(1);
    }

    private Order waiting(Instant placed, int remindersSent, Instant lastReminder) {
        Order order = new Order(new Company("Alembic"), new Gift("Pad", "Stationery", "A5", null, 7, (com.wrapzzo.backend.model.Vendor) null), OrderSource.QUICK_GIFT, "Dr. Asha", "98", mr);
        order.setId(UUID.randomUUID());
        order.setApprovalStatus(OrderApprovalStatus.PENDING);
        order.setCreatedAt(placed);
        order.setApprovalRemindersSent(remindersSent);
        order.setApprovalLastReminderAt(lastReminder);
        return order;
    }

    private static User user(String name, String emailAddress) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getName()).thenReturn(name);
        when(user.getEmail()).thenReturn(emailAddress);
        return user;
    }
}
