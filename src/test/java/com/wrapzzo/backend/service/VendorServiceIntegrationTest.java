package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.AddVendorRequest;
import com.wrapzzo.backend.dto.AddVendorResponse;
import com.wrapzzo.backend.dto.EditVendorRequest;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** VendorService reads the caller (a User, the Super Admin) from SecurityContextHolder -- set up manually, same as GiftServiceIntegrationTest. */
@SpringBootTest
@Transactional
class VendorServiceIntegrationTest {

    @Autowired
    private VendorService vendorService;

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
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addWithNoPasswordGeneratesOne() {
        AddVendorResponse response =
                vendorService.add(new AddVendorRequest("Acme Printing", "contact@acme-test.com", "1234567890", null, null));

        assertThat(response.vendor().status()).isEqualTo(VendorStatus.ACTIVE);
        assertThat(response.temporaryPassword()).isNotBlank();
    }

    @Test
    void addWithMatchingPasswordDoesNotReturnOne() {
        AddVendorResponse response = vendorService.add(
                new AddVendorRequest("Acme Printing 2", "contact2@acme-test.com", null, "SuperSecret123!", "SuperSecret123!"));

        assertThat(response.temporaryPassword()).isNull();
    }

    @Test
    void addWithMismatchedPasswordThrows() {
        assertThatThrownBy(() -> vendorService.add(
                        new AddVendorRequest("Acme Printing 3", "contact3@acme-test.com", null, "SuperSecret123!", "Different!")))
                .isInstanceOf(PasswordMismatchException.class);
    }

    @Test
    void editChangesFields() {
        AddVendorResponse added =
                vendorService.add(new AddVendorRequest("Old Name", "contact4@acme-test.com", null, null, null));

        var updated = vendorService.edit(added.vendor().id(), new EditVendorRequest("New Name", "999"));

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.contactPhone()).isEqualTo("999");
    }

    @Test
    void suspendAndReactivateToggleStatus() {
        AddVendorResponse added =
                vendorService.add(new AddVendorRequest("Toggle Vendor", "contact5@acme-test.com", null, null, null));

        vendorService.setStatus(added.vendor().id(), VendorStatus.SUSPENDED);
        assertThat(vendorService.view(added.vendor().id()).status()).isEqualTo(VendorStatus.SUSPENDED);

        vendorService.setStatus(added.vendor().id(), VendorStatus.ACTIVE);
        assertThat(vendorService.view(added.vendor().id()).status()).isEqualTo(VendorStatus.ACTIVE);
    }

    @Test
    void viewUnknownVendorThrows() {
        assertThatThrownBy(() -> vendorService.view(UUID.randomUUID())).isInstanceOf(VendorNotFoundException.class);
    }

    @Test
    void listIncludesAddedVendors() {
        AddVendorResponse added =
                vendorService.add(new AddVendorRequest("Listed Vendor", "contact6@acme-test.com", null, null, null));

        assertThat(vendorService.list()).extracting(v -> v.id()).contains(added.vendor().id());
    }
}
