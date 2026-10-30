package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CompanyResponse;
import com.wrapzzo.backend.dto.DesignationAccessResponse;
import com.wrapzzo.backend.exception.InvalidAccessControlSelectionException;
import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.RoleAccessControl;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleAccessControlRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.wrapzzo.backend.security.Permission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Same platform-staff-caller pattern as RoleServiceIntegrationTest/GiftServiceIntegrationTest --
 * DesignationAccessService goes through CompanyAccessGuard, which reads the caller from
 * SecurityContextHolder.
 */
@SpringBootTest
@Transactional
class DesignationAccessServiceIntegrationTest {

    @Autowired
    private DesignationAccessService designationAccessService;

    @Autowired
    private AccessControlCacheService accessControlCacheService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessControlRepository accessControlRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleAccessControlRepository roleAccessControlRepository;

    private UUID companyId;
    private Role companyAdminRole;

    @BeforeEach
    void seedCompanyAndPlatformStaffCaller() {
        CompanyResponse company = companyService.create("Designation Test Co " + UUID.randomUUID());
        companyId = company.id();
        companyAdminRole = roleRepository.findByCompanyIdAndName(companyId, "Company Admin").orElseThrow();

        Role platformRole = roleRepository.save(new Role(null, "Test Platform Staff " + UUID.randomUUID()));
        User platformStaff = userRepository.save(new User(
                null, platformRole, "Platform Staff", "platform-" + UUID.randomUUID() + "@wrapzzo.com", "n/a"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(platformStaff.getId(), null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User saveCompanyUserWithDesignation(String email, String designation) {
        User user = new User(companyRepository.getReferenceById(companyId), companyAdminRole, "Test User", email, "n/a");
        user.setDesignation(designation);
        return userRepository.save(user);
    }

    @Test
    void listDesignationsReflectsDistinctNonNullValues() {
        saveCompanyUserWithDesignation("a@designation-test.com", "Regional Manager");
        saveCompanyUserWithDesignation("b@designation-test.com", "Regional Manager");
        saveCompanyUserWithDesignation("c@designation-test.com", "MR");
        saveCompanyUserWithDesignation("d@designation-test.com", null);

        assertThat(designationAccessService.listDesignations(companyId)).containsExactlyInAnyOrder("MR", "Regional Manager");
    }

    @Test
    void replaceAccessControlsRoundTripsAddsImpliedViewsAndInvalidatesTheRedisCache() {
        UUID campaignEditId = accessControlRepository.findByKey("campaign.edit").orElseThrow().getId();
        UUID campaignViewId = accessControlRepository.findByKey("campaign.view").orElseThrow().getId();
        UUID userViewId = accessControlRepository.findByKey("user.view").orElseThrow().getId();

        assertThat(accessControlCacheService.getDesignationPermissionKeys(companyId, "Regional Manager")).isEmpty();

        // Editing campaigns brings viewing them along.
        DesignationAccessResponse granted =
                designationAccessService.replaceAccessControls(companyId, "Regional Manager", Set.of(campaignEditId, userViewId));
        assertThat(granted.accessControlIds()).containsExactlyInAnyOrder(campaignEditId, campaignViewId, userViewId);
        assertThat(accessControlCacheService.getDesignationPermissionKeys(companyId, "Regional Manager"))
                .containsExactlyInAnyOrder("campaign.edit", "campaign.view", "user.view");

        DesignationAccessResponse cleared = designationAccessService.replaceAccessControls(companyId, "Regional Manager", Set.of());
        assertThat(cleared.accessControlIds()).isEmpty();
        assertThat(accessControlCacheService.getDesignationPermissionKeys(companyId, "Regional Manager")).isEmpty();
    }

    @Test
    void aUsersEffectivePermissionsUnionTheirRoleGrantsWithTheirDesignationGrants() {
        // Member (campaign_entry.submit only) rather than Company Admin, which already holds every
        // company-assignable key -- so a designation grant of campaign.edit is genuinely
        // additive here, not redundant with the role.
        Role memberRole = roleRepository.save(new Role(companyRepository.getReferenceById(companyId), "Member"));
        roleAccessControlRepository.save(
                new RoleAccessControl(memberRole, accessControlRepository.findByKey("campaign_entry.submit").orElseThrow()));
        UUID campaignEditId = accessControlRepository.findByKey("campaign.edit").orElseThrow().getId();
        designationAccessService.replaceAccessControls(companyId, "Zonal Manager", Set.of(campaignEditId));

        User user = new User(companyRepository.getReferenceById(companyId), memberRole, "Test User", "zonal@designation-test.com", "n/a");
        user.setDesignation("Zonal Manager");
        userRepository.save(user);

        Set<String> effective = accessControlCacheService.resolveEffectiveKeys(user);

        // Member's own baseline role grant, proving the union isn't a replacement...
        assertThat(effective).contains("campaign_entry.submit");
        // ...plus the designation's separately-granted key.
        assertThat(effective).contains("campaign.edit");
    }

    @Test
    void platformOnlyKeysAreRejectedFromADesignationGrant() {
        UUID orderManageId = accessControlRepository.findByKey("platform_order.manage").orElseThrow().getId();

        assertThatThrownBy(() -> designationAccessService.replaceAccessControls(companyId, "Regional Manager", Set.of(orderManageId)))
                .isInstanceOf(InvalidAccessControlSelectionException.class);
    }

    @Test
    void companyAssignableCatalogExcludesPlatformOnlyKeys() {
        List<String> assignableKeys = accessControlRepository.findByCompanyAssignableTrue().stream().map(AccessControl::getKey).toList();

        assertThat(assignableKeys).noneMatch(key -> key.startsWith("platform_"));
        assertThat(assignableKeys).contains("campaign.view", "campaign_entry.submit", "request.approve", "user.edit", "access.edit");
    }

    @Test
    void aUserWithNoDesignationResolvesCleanlyToJustTheirRoleGrants() {
        User user = saveCompanyUserWithDesignation("nodesig@designation-test.com", null);

        Set<String> effective = accessControlCacheService.resolveEffectiveKeys(user);

        assertThat(effective).containsExactlyInAnyOrder(Permission.companyAssignablePermissions().stream().map(Permission::key).toArray(String[]::new));
    }

    @Test
    void anInvalidAccessControlIdIsRejected() {
        assertThatThrownBy(() -> designationAccessService.replaceAccessControls(companyId, "Ghost Designation", Set.of(UUID.randomUUID())))
                .isInstanceOf(InvalidAccessControlSelectionException.class);
    }

    @Test
    void promotingAUserToCompanyAdminPropagatesTheirDesignationsGrantsToAnyoneSharingIt() {
        User promoted = saveCompanyUserWithDesignation("promoted@designation-test.com", "Zonal Manager");
        // Never personally promoted -- only shares the same designation as someone who was.
        User peer = saveCompanyUserWithDesignation("peer@designation-test.com", "Zonal Manager");

        userService.makeCompanyAdmin(companyId, promoted.getId());

        Set<String> peerEffective = accessControlCacheService.resolveEffectiveKeys(peer);
        assertThat(peerEffective).containsExactlyInAnyOrder(Permission.companyAssignablePermissions().stream().map(Permission::key).toArray(String[]::new));
    }

    @Test
    void demotingBackToMemberDoesNotRevertTheDesignationsPropagatedGrants() {
        // "Member" is normally auto-created by bulkImport's findOrCreateMemberRole -- this test
        // never bulk-imports, so removeCompanyAdmin (which reassigns onto it) needs it seeded directly.
        roleRepository.save(new Role(companyRepository.getReferenceById(companyId), "Member"));

        User anotherAdmin = saveCompanyUserWithDesignation("admin2@designation-test.com", "Other Manager");
        userService.makeCompanyAdmin(companyId, anotherAdmin.getId());
        User promoted = saveCompanyUserWithDesignation("promoted2@designation-test.com", "Area Manager");
        userService.makeCompanyAdmin(companyId, promoted.getId());

        userService.removeCompanyAdmin(companyId, promoted.getId());

        assertThat(designationAccessService.getAccessControls(companyId, "Area Manager").accessControlIds()).isNotEmpty();
    }

    @Test
    void aDesignationsAccessCarriesWhatItsUsersGetFromTheirRole() {
        Role memberRole = roleRepository.save(new Role(companyRepository.getReferenceById(companyId), "Member"));
        UUID quickGiftId = accessControlRepository.findByKey("request.create").orElseThrow().getId();
        roleAccessControlRepository.save(new RoleAccessControl(memberRole, accessControlRepository.findByKey("request.create").orElseThrow()));
        for (String email : List.of("mr1@designation-test.com", "mr2@designation-test.com")) {
            User mr = new User(companyRepository.getReferenceById(companyId), memberRole, "MR", email, "n/a");
            mr.setDesignation("Medical Representative");
            userRepository.save(mr);
        }

        DesignationAccessResponse access = designationAccessService.getAccessControls(companyId, "Medical Representative");

        // Nothing granted to the designation itself, but its users' role still shows.
        assertThat(access.accessControlIds()).isEmpty();
        assertThat(access.roles()).singleElement().satisfies(role -> {
            assertThat(role.roleName()).isEqualTo("Member");
            assertThat(role.userCount()).isEqualTo(2);
            assertThat(role.accessControlIds()).containsExactly(quickGiftId);
        });
    }

    @Test
    void aGrantMadeStraightInTheDatabaseShowsOnceTheCacheIsClearedAtStartup() {
        Role memberRole = roleRepository.save(new Role(companyRepository.getReferenceById(companyId), "Member"));
        User user = userRepository.save(new User(companyRepository.getReferenceById(companyId), memberRole, "MR", "mr@cache-test.com", "n/a"));
        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).doesNotContain("menu.edit");

        // What a deploy's migration does: insert the grant directly, bypassing every invalidation.
        roleAccessControlRepository.save(new RoleAccessControl(memberRole, accessControlRepository.findByKey("menu.edit").orElseThrow()));
        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).doesNotContain("menu.edit");

        assertThat(accessControlCacheService.clearAll()).isPositive();
        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).contains("menu.edit");
    }
}
