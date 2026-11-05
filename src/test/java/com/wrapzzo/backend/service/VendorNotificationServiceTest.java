package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What a vendor is told as an order moves through its hands -- and what it is not. */
class VendorNotificationServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderDesignRepository designs = mock(OrderDesignRepository.class);
    private final VendorRepository vendors = mock(VendorRepository.class);
    private final TestEmailSenderConfig.RecordingEmailSender email = new TestEmailSenderConfig.RecordingEmailSender();
    private final VendorNotificationService service = new VendorNotificationService(orders, designs, vendors, email, "https://app.example");

    private final UUID orderId = UUID.randomUUID();
    private final Order order = mock(Order.class);

    private Vendor vendor(String name, String contact, VendorStatus status) {
        Vendor v = mock(Vendor.class);
        UUID id = UUID.randomUUID();
        when(v.getId()).thenReturn(id);
        when(v.getName()).thenReturn(name);
        when(v.getContactEmail()).thenReturn(contact);
        when(v.getStatus()).thenReturn(status);
        when(vendors.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    private void anOrder(boolean artworkRequired) {
        Gift gift = mock(Gift.class);
        when(gift.getName()).thenReturn("Prescription Pad");
        when(gift.isArtworkRequired()).thenReturn(artworkRequired);
        Company company = mock(Company.class);
        when(company.getName()).thenReturn("Alembic");
        when(order.getId()).thenReturn(orderId);
        when(order.getOrderNo()).thenReturn(101);
        when(order.getGift()).thenReturn(gift);
        when(order.getCompany()).thenReturn(company);
        when(order.getQuantity()).thenReturn(200);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
    }

    @Test
    void aNewOrderEmailsTheVendorWhatToDoNextAndLinksToIt() {
        anOrder(true);
        Vendor v = vendor("Pad Printers", "pads@example.com", VendorStatus.ACTIVE);

        service.orderAssigned(orderId, v.getId(), null);

        assertThat(email.sent).singleElement().satisfies(sent -> {
            assertThat(sent.to()).isEqualTo("pads@example.com");
            assertThat(sent.subject()).isEqualTo("New order ORD-101: Prescription Pad x200");
            assertThat(sent.html()).contains("Upload an artwork proof").contains("Alembic").contains("https://app.example/vendor/orders/" + orderId);
        });
    }

    @Test
    void anOrderWithNoArtworkSaysToJustProduceIt() {
        anOrder(false);
        Vendor v = vendor("Pad Printers", "pads@example.com", VendorStatus.ACTIVE);

        service.orderAssigned(orderId, v.getId(), null);

        assertThat(email.sent.get(0).html()).contains("No artwork proof is needed");
    }

    @Test
    void theVendorAnOrderWasTakenFromIsToldSo() {
        anOrder(true);
        Vendor now = vendor("New Printers", "new@example.com", VendorStatus.ACTIVE);
        Vendor before = vendor("Old Printers", "old@example.com", VendorStatus.ACTIVE);

        service.orderAssigned(orderId, now.getId(), before.getId());

        assertThat(email.sent).extracting(TestEmailSenderConfig.RecordingEmailSender.Sent::to).containsExactlyInAnyOrder("new@example.com", "old@example.com");
        assertThat(email.sent.stream().filter(s -> s.to().equals("old@example.com")).findFirst().orElseThrow().subject()).contains("no longer assigned");
    }

    @Test
    void aSuspendedVendorOrOneWithNoEmailIsNotMailed() {
        anOrder(true);
        Vendor suspended = vendor("Off", "off@example.com", VendorStatus.SUSPENDED);
        Vendor none = vendor("None", " ", VendorStatus.ACTIVE);

        service.orderAssigned(orderId, suspended.getId(), null);
        service.orderAssigned(orderId, none.getId(), null);

        assertThat(email.sent).isEmpty();
    }

    private OrderDesign design(Vendor v, String comment) {
        OrderDesign d = mock(OrderDesign.class);
        UUID id = UUID.randomUUID();
        when(d.getId()).thenReturn(id);
        when(d.getUploadedBy()).thenReturn(v);
        when(d.getOrder()).thenReturn(order);
        when(d.getVersionNo()).thenReturn(2);
        when(d.getReviewComment()).thenReturn(comment);
        when(designs.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void aProofSentBackCarriesTheReviewersNote_escaped_andNamesNoOne() {
        anOrder(true);
        Vendor v = vendor("Pad Printers", "pads@example.com", VendorStatus.ACTIVE);
        OrderDesign d = design(v, "Fix the name <b>spelling</b>");

        service.proofDecided(d.getId(), false);

        assertThat(email.sent).singleElement().satisfies(sent -> {
            assertThat(sent.subject()).isEqualTo("Changes requested on your proof v2 for ORD-101");
            assertThat(sent.html()).contains("Fix the name &lt;b&gt;spelling&lt;/b&gt;").doesNotContain("<b>spelling</b>");
        });
    }

    @Test
    void anApprovedProofTellsTheVendorItMayShip() {
        anOrder(true);
        Vendor v = vendor("Pad Printers", "pads@example.com", VendorStatus.ACTIVE);
        OrderDesign d = design(v, null);

        service.proofDecided(d.getId(), true);

        assertThat(email.sent).singleElement().satisfies(sent -> {
            assertThat(sent.subject()).isEqualTo("Approved: proof v2 for ORD-101");
            assertThat(sent.html()).contains("mark it dispatched");
        });
    }
}
