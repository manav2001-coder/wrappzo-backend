package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.AddReferencesResponse;
import com.wrapzzo.backend.dto.BulkUserTemplatesResponse;
import com.wrapzzo.backend.dto.RoleRequest;
import com.wrapzzo.backend.dto.RoleResponse;
import com.wrapzzo.backend.dto.TemplateRequest;
import com.wrapzzo.backend.dto.TemplateResponse;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.InvalidTemplateException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** The roles master and access templates, called as platform staff (company null), like the other service tests. */
@SpringBootTest
@Transactional
class TemplateAndRoleIntegrationTest {

    @Autowired private RoleService roleService;
    @Autowired private TemplateService templateService;
    @Autowired private FeatureService featureService;
    @Autowired private AccessControlCacheService accessControlCacheService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessControlRepository accessControlRepository;

    private Company company;
    private Company otherCompany;
    private Set<UUID> orderView;
    private Set<UUID> userView;

    @BeforeEach
    void seed() {
        company = companyRepository.save(new Company("Template Test Co"));
        otherCompany = companyRepository.save(new Company("Other Template Co"));
        Role platformRole = roleRepository.save(new Role(null, "Test Platform Staff " + UUID.randomUUID()));
        User platformStaff = userRepository.save(
                new User(null, platformRole, "Platform Staff", "platform-" + UUID.randomUUID() + "@wrapzzo.com", "n/a"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(platformStaff.getId(), null, List.of()));
        orderView = Set.of(accessControlRepository.findByKey("order.view").orElseThrow().getId());
        userView = Set.of(accessControlRepository.findByKey("user.view").orElseThrow().getId());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private TemplateResponse template(UUID companyId, String name, Set<UUID> keys, Set<UUID> includes) {
        return templateService.create(companyId, new TemplateRequest(name, null, null, keys, includes));
    }

    @Test
    void rolesAreCreatedEditedAndRemovedPerCompany() {
        RoleResponse created = roleService.create(company.getId(), new RoleRequest("Zonal Head", "Looks after a zone", null, null));
        assertThat(roleService.list(company.getId())).extracting(RoleResponse::name).contains("Zonal Head");
        assertThat(roleService.list(otherCompany.getId())).extracting(RoleResponse::name).doesNotContain("Zonal Head");

        RoleResponse renamed = roleService.update(company.getId(), created.id(), new RoleRequest("Zone Lead", null, null, null));
        assertThat(renamed.name()).isEqualTo("Zone Lead");

        roleService.delete(company.getId(), created.id());
        assertThat(roleService.list(company.getId())).extracting(RoleResponse::name).doesNotContain("Zone Lead");
    }

    @Test
    void aRoleNameIsUniqueWithinACompanyButFreeInAnother() {
        roleService.create(company.getId(), new RoleRequest("Auditor", null, null, null));

        assertThatThrownBy(() -> roleService.create(company.getId(), new RoleRequest("Auditor", null, null, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(roleService.create(otherCompany.getId(), new RoleRequest("Auditor", null, null, null)).name()).isEqualTo("Auditor");
    }

    @Test
    void protectedRolesCannotBeRenamedDeactivatedOrRemoved() {
        Role admin = roleRepository.save(new Role(company, "Company Admin"));

        assertThatThrownBy(() -> roleService.update(company.getId(), admin.getId(), new RoleRequest("Boss", null, null, null)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> roleService.update(company.getId(), admin.getId(), new RoleRequest("Company Admin", null, false, null)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> roleService.delete(company.getId(), admin.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void aRoleHeldByUsersCannotBeRemoved() {
        RoleResponse role = roleService.create(company.getId(), new RoleRequest("Temp", null, null, null));
        userRepository.save(new User(company, roleRepository.findById(role.id()).orElseThrow(), "Held", "held-" + UUID.randomUUID() + "@x.com", "h"));

        assertThatThrownBy(() -> roleService.delete(company.getId(), role.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void aRoleGrantsOnlyWhatItsTemplatesGrant() {
        TemplateResponse orders = template(company.getId(), "Orders", orderView, Set.of());
        RoleResponse role = roleService.create(company.getId(), new RoleRequest("Orderer", null, null, Set.of(orders.id())));
        User user = userRepository.save(new User(company, roleRepository.findById(role.id()).orElseThrow(), "O", "o-" + UUID.randomUUID() + "@x.com", "h"));
        accessControlCacheService.invalidateAll();

        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).containsExactly("order.view");

        roleService.replaceTemplates(company.getId(), role.id(), Set.of());
        accessControlCacheService.invalidateAll();

        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).isEmpty();
    }

    @Test
    void anIncludedTemplateAddsItsKeysToTheIncluder() {
        TemplateResponse base = template(company.getId(), "Base", orderView, Set.of());
        TemplateResponse top = template(company.getId(), "Top", userView, Set.of(base.id()));

        assertThat(top.effectiveAccessControlIds()).containsAll(orderView).containsAll(userView);
        assertThat(top.accessControlIds()).isEqualTo(userView);
    }

    @Test
    void grantingAnActionBringsItsModulesViewAlong() {
        Set<UUID> campaignEdit = Set.of(accessControlRepository.findByKey("campaign.edit").orElseThrow().getId());

        TemplateResponse created = template(company.getId(), "Editors", campaignEdit, Set.of());

        UUID campaignView = accessControlRepository.findByKey("campaign.view").orElseThrow().getId();
        assertThat(created.accessControlIds()).contains(campaignView);
    }

    @Test
    void aTemplateCannotIncludeItselfOrCloseALoop() {
        TemplateResponse a = template(company.getId(), "A", orderView, Set.of());
        TemplateResponse b = template(company.getId(), "B", userView, Set.of(a.id()));

        assertThatThrownBy(() -> templateService.update(a.id(), new TemplateRequest("A", null, null, orderView, Set.of(a.id()))))
                .isInstanceOf(InvalidTemplateException.class);
        assertThatThrownBy(() -> templateService.update(a.id(), new TemplateRequest("A", null, null, orderView, Set.of(b.id()))))
                .isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    void aCompanyTemplateCannotHoldPlatformOnlyKeysOrIncludeAnotherCompanysTemplate() {
        Set<UUID> platformOnly = Set.of(accessControlRepository.findByKey("platform_company.view").orElseThrow().getId());
        TemplateResponse foreign = template(otherCompany.getId(), "Foreign", orderView, Set.of());

        assertThatThrownBy(() -> template(company.getId(), "Greedy", platformOnly, Set.of())).isInstanceOf(InvalidTemplateException.class);
        assertThatThrownBy(() -> template(company.getId(), "Borrower", orderView, Set.of(foreign.id()))).isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    void aCompanyCannotMapAnotherCompanysTemplateToItsRole() {
        TemplateResponse foreign = template(otherCompany.getId(), "Foreign", orderView, Set.of());
        RoleResponse role = roleService.create(company.getId(), new RoleRequest("Mapper", null, null, null));

        assertThatThrownBy(() -> roleService.replaceTemplates(company.getId(), role.id(), Set.of(foreign.id())))
                .isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    void cloningASystemMasterGivesTheCompanyAnEditableCopy() {
        TemplateResponse master = templateService.create(null, new TemplateRequest("Master " + UUID.randomUUID(), null, null, orderView, Set.of()));

        TemplateResponse copy = templateService.clone(company.getId(), master.id(), "My copy");

        assertThat(copy.system()).isFalse();
        assertThat(copy.companyId()).isEqualTo(company.getId());
        assertThat(copy.accessControlIds()).isEqualTo(master.accessControlIds());
        assertThat(templateService.update(copy.id(), new TemplateRequest("My copy", "edited", null, userView, Set.of())).description())
                .isEqualTo("edited");
    }

    @Test
    void aTemplateInUseCannotBeDeleted() {
        TemplateResponse used = template(company.getId(), "Used", orderView, Set.of());
        roleService.create(company.getId(), new RoleRequest("Uses it", null, null, Set.of(used.id())));

        assertThatThrownBy(() -> templateService.delete(used.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void addingIncludesJudgesEachCandidateAlone() {
        TemplateResponse top = template(company.getId(), "Top", orderView, Set.of());
        TemplateResponse good = template(company.getId(), "Good", userView, Set.of());
        TemplateResponse foreign = template(otherCompany.getId(), "Foreign", orderView, Set.of());

        AddReferencesResponse result = templateService.addReferences(top.id(), Set.of(good.id(), foreign.id(), top.id()));

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.totalRequested()).isEqualTo(3);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.template().referencedTemplateIds()).containsExactly(good.id());
        assertThat(templateService.addReferences(top.id(), Set.of(good.id())).errors()).extracting(AddReferencesResponse.Rejection::reason)
                .containsExactly("It is already included");
    }

    @Test
    void anIncludeThatWouldLoopIsRejectedWithoutBlockingTheRest() {
        TemplateResponse a = template(company.getId(), "A", orderView, Set.of());
        TemplateResponse b = template(company.getId(), "B", userView, Set.of(a.id()));

        AddReferencesResponse result = templateService.addReferences(a.id(), Set.of(b.id()));

        assertThat(result.added()).isZero();
        assertThat(result.errors().get(0).reason()).contains("loop");
    }

    @Test
    void anIncludeCanBeRemoved() {
        TemplateResponse base = template(company.getId(), "Base", orderView, Set.of());
        TemplateResponse top = template(company.getId(), "Top", userView, Set.of(base.id()));

        assertThat(templateService.removeReference(top.id(), base.id()).referencedTemplateIds()).isEmpty();
    }

    @Test
    void bulkAssignAddsTemplatesToEachUserAndIsolatesFailures() {
        TemplateResponse orders = template(company.getId(), "Orders", orderView, Set.of());
        Role member = roleRepository.save(new Role(company, "Member"));
        User one = userRepository.save(new User(company, member, "One", "one-" + UUID.randomUUID() + "@x.com", "h"));
        User foreignUser = userRepository.save(new User(otherCompany, roleRepository.save(new Role(otherCompany, "Member")), "Two", "two-" + UUID.randomUUID() + "@x.com", "h"));

        BulkUserTemplatesResponse first = templateService.bulkAssignUserTemplates(company.getId(), Set.of(one.getId(), foreignUser.getId()), Set.of(orders.id()));
        BulkUserTemplatesResponse again = templateService.bulkAssignUserTemplates(company.getId(), Set.of(one.getId()), Set.of(orders.id()));

        assertThat(first.results()).extracting(BulkUserTemplatesResponse.UserResult::status).containsExactlyInAnyOrder("ok", "failed");
        assertThat(templateService.userTemplateIds(company.getId(), one.getId())).containsExactly(orders.id());
        assertThat(again.results().get(0).added()).isZero();
        assertThat(again.results().get(0).skipped()).isEqualTo(1);
    }

    @Test
    void aSwitchedOffFeatureGrantsNothingEvenWhereATemplateCarriesIt() {
        TemplateResponse orders = template(company.getId(), "Orders", orderView, Set.of());
        RoleResponse role = roleService.create(company.getId(), new RoleRequest("Orderer", null, null, Set.of(orders.id())));
        User user = userRepository.save(new User(company, roleRepository.findById(role.id()).orElseThrow(), "O", "o-" + UUID.randomUUID() + "@x.com", "h"));

        featureService.setActive(orderView.iterator().next(), false);
        accessControlCacheService.invalidateAll();

        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).isEmpty();
        assertThat(featureService.list()).filteredOn(feature -> feature.key().equals("order.view")).allMatch(feature -> !feature.active());
    }

    @Test
    void theKeysThatRunTheAccessScreensCannotBeSwitchedOff() {
        UUID templateView = accessControlRepository.findByKey("template.view").orElseThrow().getId();

        assertThatThrownBy(() -> featureService.setActive(templateView, false)).isInstanceOf(ConflictException.class);
    }
}
