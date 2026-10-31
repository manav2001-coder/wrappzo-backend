package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.dto.BulkImportRoleRow;
import com.wrapzzo.backend.dto.BulkImportRolesResponse;
import com.wrapzzo.backend.exception.UserImportValidationException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleServiceBulkImportTest {

    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
    private final AccessControlCacheService cache = mock(AccessControlCacheService.class);
    private final RoleService service = new RoleService(roles, users, companies, guard, cache);

    private final UUID companyId = UUID.randomUUID();
    private final Company company = new Company();

    private Role roleNamed(String name, String description) {
        Role role = new Role(company, name);
        role.setDescription(description);
        return role;
    }

    private void companyHas(Role... existing) {
        company.setId(companyId);
        when(companies.findById(companyId)).thenReturn(Optional.of(company));
        when(roles.findByCompanyId(companyId)).thenReturn(List.of(existing));
    }

    @Test
    void createsNewRolesAndUpdatesTheDescriptionOfOnesTheCompanyHas() {
        Role zbm = roleNamed("ZBM", "old");
        Role rm = roleNamed("RM", "kept");
        companyHas(zbm, rm);

        BulkImportRolesResponse result = service.bulkImport(
                companyId,
                List.of(new BulkImportRoleRow(" MR ", "Medical Representative"), new BulkImportRoleRow("zbm", "Zonal manager"), new BulkImportRoleRow("RM", "")));

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(zbm.getDescription()).isEqualTo("Zonal manager");
        assertThat(rm.getDescription()).isEqualTo("kept");
        verify(roles).save(any(Role.class));
        verify(cache).invalidateAfterCommit(companyId);
    }

    @Test
    void writesNothingWhenAnyRowIsInvalid() {
        companyHas();

        assertThatThrownBy(() -> service.bulkImport(
                        companyId,
                        List.of(new BulkImportRoleRow("MR", null), new BulkImportRoleRow("mr", null), new BulkImportRoleRow("Company Admin", null))))
                .isInstanceOfSatisfying(UserImportValidationException.class, e -> assertThat(e.getErrors()).hasSize(2));

        verify(roles, never()).save(any(Role.class));
    }
}
