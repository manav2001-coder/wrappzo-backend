package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.AddVendorRequest;
import com.wrapzzo.backend.dto.EditVendorRequest;
import com.wrapzzo.backend.dto.VendorResponse;
import com.wrapzzo.backend.exception.VendorEmailAlreadyExistsException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** VendorService reads the caller (a User, the Super Admin) from SecurityContextHolder -- set up manually, same as GiftServiceIntegrationTest. */
@SpringBootTest
@Import(TestEmailSenderConfig.class)
@Transactional
class VendorServiceIntegrationTest {

    @Autowired
    private VendorService vendorService;

    @Autowired
    private TestEmailSenderConfig.RecordingEmailSender emailSender;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void actAsAUser() {
        Company company = companyRepository.save(new Company("Vendor Test Co"));
        Role role = roleRepository.save(new Role(company, "Whatever Role"));
        User user = userRepository.save(new User(company, role, "Test Admin", "vendor-test-" + UUID.randomUUID() + "@wrapzzo.com", "n/a"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
        // The recording bean is a singleton -- @Transactional rollback doesn't clear its
        // in-memory list, so each test starts with a clean slate explicitly.
        emailSender.sent.clear();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addSendsAnInviteEmail() {
        VendorResponse vendor =
                vendorService.add(new AddVendorRequest("Acme Printing", "contact@acme-test.com", "1234567890", null, null));

        assertThat(vendor.status()).isEqualTo(VendorStatus.ACTIVE);
        // Regression check: vendorNo is DB-generated (@Generated INSERT) and only readable
        // immediately if the service flushes rather than deferring the insert to commit.
        assertThat(vendor.displayId()).matches("VID-\\d+");
        assertThat(emailSender.sent).hasSize(1);
        assertThat(emailSender.sent.get(0).to()).isEqualTo("contact@acme-test.com");
        assertThat(emailSender.sent.get(0).html()).contains("/vendor/setup-password?token=");
    }

    @Test
    void addWithDuplicateEmailThrows() {
        vendorService.add(new AddVendorRequest("First", "dup@acme-test.com", null, null, null));

        assertThatThrownBy(() -> vendorService.add(new AddVendorRequest("Second", "dup@acme-test.com", null, null, null)))
                .isInstanceOf(VendorEmailAlreadyExistsException.class);
    }

    @Test
    void resendInviteSendsANewEmail() {
        VendorResponse vendor = vendorService.add(new AddVendorRequest("Resend Vendor", "resend@acme-test.com", null, null, null));

        vendorService.resendInvite(vendor.id());

        assertThat(emailSender.sent).hasSize(2);
    }

    @Test
    void editChangesFields() {
        VendorResponse added = vendorService.add(new AddVendorRequest("Old Name", "contact4@acme-test.com", null, null, null));

        var updated = vendorService.edit(added.id(), new EditVendorRequest("New Name", "999", null, null));

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.contactPhone()).isEqualTo("999");
    }

    @Test
    void addAndEditPersistMarkupFeesAndCommission() {
        VendorResponse added = vendorService.add(new AddVendorRequest(
                "Commercial Terms Vendor", "commercial-terms@acme-test.com", null, new BigDecimal("12.50"), new BigDecimal("5.00")));

        assertThat(added.markupFees()).isEqualByComparingTo("12.50");
        assertThat(added.commission()).isEqualByComparingTo("5.00");

        var updated = vendorService.edit(
                added.id(), new EditVendorRequest("Commercial Terms Vendor", null, new BigDecimal("20.00"), new BigDecimal("7.50")));

        assertThat(updated.markupFees()).isEqualByComparingTo("20.00");
        assertThat(updated.commission()).isEqualByComparingTo("7.50");
    }

    @Test
    void suspendAndReactivateToggleStatus() {
        VendorResponse added = vendorService.add(new AddVendorRequest("Toggle Vendor", "contact5@acme-test.com", null, null, null));

        vendorService.setStatus(added.id(), VendorStatus.SUSPENDED);
        assertThat(vendorService.view(added.id()).status()).isEqualTo(VendorStatus.SUSPENDED);

        vendorService.setStatus(added.id(), VendorStatus.ACTIVE);
        assertThat(vendorService.view(added.id()).status()).isEqualTo(VendorStatus.ACTIVE);
    }

    @Test
    void viewUnknownVendorThrows() {
        assertThatThrownBy(() -> vendorService.view(UUID.randomUUID())).isInstanceOf(VendorNotFoundException.class);
    }

    @Test
    void listIncludesAddedVendors() {
        VendorResponse added = vendorService.add(new AddVendorRequest("Listed Vendor", "contact6@acme-test.com", null, null, null));

        assertThat(vendorService.list(null)).extracting(VendorResponse::id).contains(added.id());
    }
}
