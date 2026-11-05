package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.AddVendorRequest;
import com.wrapzzo.backend.dto.VendorResponse;
import com.wrapzzo.backend.exception.VendorEmailAlreadyExistsException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
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

/** VendorService reads the caller (a User) from SecurityContextHolder -- set up manually, same as GiftServiceIntegrationTest. */
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

    @Autowired
    private CompanyVendorRepository companyVendorRepository;

    @Autowired
    private OrgUnitRepository orgUnitRepository;

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

    private Company owner() {
        return companyRepository.save(new Company("Owner Co " + UUID.randomUUID()));
    }

    @Test
    void addSendsAnInviteEmailAndRecordsTheOwner() {
        Company owner = owner();
        Vendor vendor = vendorService.add(new AddVendorRequest("Acme Printing", "contact@acme-test.com", "1234567890", null, null), owner);

        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.ACTIVE);
        assertThat(vendor.getCompany().getId()).isEqualTo(owner.getId());
        // Regression check: vendorNo is DB-generated (@Generated INSERT) and only readable
        // immediately if the service flushes rather than deferring the insert to commit.
        assertThat(vendorService.toResponse(vendor).displayId()).matches("VID-\\d+");
        assertThat(emailSender.sent).hasSize(1);
        assertThat(emailSender.sent.get(0).to()).isEqualTo("contact@acme-test.com");
        assertThat(emailSender.sent.get(0).html()).contains("/vendor/setup-password?token=");
    }

    @Test
    void addWithDuplicateEmailThrows() {
        Company owner = owner();
        vendorService.add(new AddVendorRequest("First", "dup@acme-test.com", null, null, null), owner);

        assertThatThrownBy(() -> vendorService.add(new AddVendorRequest("Second", "dup@acme-test.com", null, null, null), owner))
                .isInstanceOf(VendorEmailAlreadyExistsException.class);
    }

    @Test
    void resendInviteSendsANewEmail() {
        Vendor vendor = vendorService.add(new AddVendorRequest("Resend Vendor", "resend@acme-test.com", null, null, null), owner());

        vendorService.resendInvite(vendor);

        assertThat(emailSender.sent).hasSize(2);
    }

    @Test
    void editDetailsChangesNameAndPhoneButNeverTheCommercialTerms() {
        Vendor added = vendorService.add(new AddVendorRequest("Old Name", "contact4@acme-test.com", null, new BigDecimal("12.50"), new BigDecimal("5.00")), owner());

        Vendor updated = vendorService.editDetails(added, "New Name", "999");

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getContactPhone()).isEqualTo("999");
        assertThat(updated.getMarkupFees()).isEqualByComparingTo("12.50");
        assertThat(updated.getCommission()).isEqualByComparingTo("5.00");
    }

    @Test
    void suspendAndReactivateToggleStatus() {
        Vendor added = vendorService.add(new AddVendorRequest("Toggle Vendor", "contact5@acme-test.com", null, null, null), owner());

        vendorService.setStatus(added, VendorStatus.SUSPENDED);
        assertThat(vendorService.view(added.getId()).status()).isEqualTo(VendorStatus.SUSPENDED);

        vendorService.setStatus(added, VendorStatus.ACTIVE);
        assertThat(vendorService.view(added.getId()).status()).isEqualTo(VendorStatus.ACTIVE);
    }

    @Test
    void viewUnknownVendorThrows() {
        assertThatThrownBy(() -> vendorService.view(UUID.randomUUID())).isInstanceOf(VendorNotFoundException.class);
    }

    @Test
    void platformStaffSeeEveryVendorAcrossCompaniesWithWhereEachServesAndWhoOwnsIt() {
        Company alpha = owner();
        Company beta = owner();
        Vendor a = vendorService.add(new AddVendorRequest("Alpha Printer", "alpha-p@acme-test.com", null, null, null), alpha);
        Vendor b = vendorService.add(new AddVendorRequest("Beta Printer", "beta-p@acme-test.com", null, null, null), beta);
        companyVendorRepository.save(new CompanyVendor(alpha, a, null));
        companyVendorRepository.save(new CompanyVendor(beta, b, orgUnitRepository.save(new OrgUnit(beta, null, "Summit", "Division"))));

        List<VendorResponse> all = vendorService.list(null);

        assertThat(all).extracting(VendorResponse::id).contains(a.getId(), b.getId());
        VendorResponse alphaRow = all.stream().filter(v -> v.id().equals(a.getId())).findFirst().orElseThrow();
        assertThat(alphaRow.companyName()).isEqualTo(alpha.getName());
        assertThat(alphaRow.serves()).containsExactly(alpha.getName() + " · Whole company");
        VendorResponse betaRow = all.stream().filter(v -> v.id().equals(b.getId())).findFirst().orElseThrow();
        assertThat(betaRow.serves()).containsExactly(beta.getName() + " · Summit");
        assertThat(vendorService.list(beta.getId())).extracting(VendorResponse::id).containsExactly(b.getId());
    }
}
