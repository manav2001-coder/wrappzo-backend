package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Whoever's approval was skipped is told who approved in their place -- and only people who can be emailed. */
class ArtworkBypassNotificationServiceTest {

    private final OrderDesignRepository designs = mock(OrderDesignRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TestEmailSenderConfig.RecordingEmailSender email = new TestEmailSenderConfig.RecordingEmailSender();
    private final ArtworkBypassNotificationService service = new ArtworkBypassNotificationService(designs, users, email, "https://app.example");

    private final User asha = user("Asha", "asha@example.com", UserStatus.ACTIVE);
    private final User ravi = user("Ravi <RM>", "ravi@example.com", UserStatus.ACTIVE);
    private final User gone = user("Gone", "gone@example.com", UserStatus.SUSPENDED);
    private final User noEmail = user("Nomail", null, UserStatus.ACTIVE);
    private final User hari = user("Hari", "hari@example.com", UserStatus.ACTIVE);

    private OrderDesign design() {
        Order order = new Order();
        Gift gift = new Gift();
        gift.setName("Prescription Pad");
        order.setGift(gift);
        try {
            var orderNo = Order.class.getDeclaredField("orderNo");
            orderNo.setAccessible(true);
            orderNo.set(order, 42);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        OrderDesign design = new OrderDesign(order, 3, "key", "proof.pdf", new Vendor());
        design.setId(UUID.randomUUID());
        when(designs.findById(design.getId())).thenReturn(Optional.of(design));
        return design;
    }

    private static User user(String name, String emailAddress, UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setEmail(emailAddress);
        user.setStatus(status);
        return user;
    }

    @Test
    void tellsEachSkippedPersonWhoApprovedInTheirPlaceWithTheNoteAndNoVendor() {
        OrderDesign design = design();
        when(users.findById(hari.getId())).thenReturn(Optional.of(hari));
        when(users.findAllById(List.of(asha.getId(), ravi.getId()))).thenReturn(List.of(asha, ravi));

        service.notifySkipped(new ArtworkBypassedEvent(design.getId(), hari.getId(), List.of(asha.getId(), ravi.getId()), "On leave <b>"));

        assertThat(email.sent).extracting(TestEmailSenderConfig.RecordingEmailSender.Sent::to).containsExactly("asha@example.com", "ravi@example.com");
        var first = email.sent.get(0);
        assertThat(first.subject()).isEqualTo("Artwork proof ORD-42 was approved in your place");
        assertThat(first.html()).contains("Hari approved the artwork proof for order ORD-42 (Prescription Pad, version 3) in your place")
                .contains("https://app.example/approvals").contains("On leave &lt;b&gt;").doesNotContain("<b>");
        // Names are escaped.
        assertThat(email.sent.get(1).html()).contains("Ravi &lt;RM&gt;");
    }

    @Test
    void skipsPeopleWhoCannotBeEmailed_andSaysNothingAboutANoteThatWasNotGiven() {
        OrderDesign design = design();
        when(users.findById(hari.getId())).thenReturn(Optional.of(hari));
        when(users.findAllById(List.of(gone.getId(), noEmail.getId(), asha.getId()))).thenReturn(List.of(gone, noEmail, asha));

        service.notifySkipped(new ArtworkBypassedEvent(design.getId(), hari.getId(), List.of(gone.getId(), noEmail.getId(), asha.getId()), null));

        assertThat(email.sent).extracting(TestEmailSenderConfig.RecordingEmailSender.Sent::to).containsExactly("asha@example.com");
        assertThat(email.sent.get(0).html()).doesNotContain("Their note");
    }
}
