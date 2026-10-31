package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.AddReferencesResponse;
import com.wrapzzo.backend.dto.BulkAssignUserTemplatesResponse;
import com.wrapzzo.backend.dto.EffectiveFeaturesResponse;
import com.wrapzzo.backend.dto.RoleRequest;
import com.wrapzzo.backend.dto.RoleResponse;
import com.wrapzzo.backend.dto.TbacFeatureResponse;
import com.wrapzzo.backend.dto.TbacTemplateResponse;
import com.wrapzzo.backend.dto.TemplateWriteRequest;
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

/** Access control: features per company, templates, and the roles and users that carry them. Called as platform staff, who name the company. */
@SpringBootTest
@Transactional
class TbacIntegrationTest {

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
    private UUID orderView;
    private UUID userView;

    @BeforeEach
    void seed() {
        company = companyRepository.save(new Company("Template Test Co"));
        otherCompany = companyRepository.save(new Company("Other Template Co"));
        featureService.registerAll(company);
        featureService.registerAll(otherCompany);
        Role platformRole = roleRepository.save(new Role(null, "Test Platform Staff " + UUID.randomUUID()));
        User platformStaff = userRepository.save(
                new User(null, platformRole, "Platform Staff", "platform-" + UUID.randomUUID() + "@wrapzzo.com", "n/a"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(platformStaff.getId(), null, List.of()));
        orderView = accessControlRepository.findByKey("order.view").orElseThrow().getId();
        userView = accessControlRepository.findByKey("user.view").orElseThrow().getId();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private TbacTemplateResponse template(Company owner, String name) {
        return templateService.create(owner == null ? null : owner.getId(), new TemplateWriteRequest(name, null, null, null, null));
    }

    private User holder(Role role) {
        return userRepository.save(new User(company, role, "H", "h-" + UUID.randomUUID() + "@x.com", "h"));
    }

    // --- roles master ---

    @Test
    void rolesAreCreatedEditedAndRemovedPerCompany() {
        RoleResponse created = roleService.create(company.getId(), new RoleRequest("Zonal Head", "Looks after a zone", null));
        assertThat(roleService.list(company.getId())).extracting(RoleResponse::name).contains("Zonal Head");
        assertThat(roleService.list(otherCompany.getId())).extracting(RoleResponse::name).doesNotContain("Zonal Head");

        assertThat(roleService.update(company.getId(), created.id(), new RoleRequest("Zone Lead", null, null)).name()).isEqualTo("Zone Lead");

        roleService.delete(company.getId(), created.id());
        assertThat(roleService.list(company.getId())).extracting(RoleResponse::name).doesNotContain("Zone Lead");
    }

    @Test
    void aRoleNameIsUniqueWithinACompanyButFreeInAnother() {
        roleService.create(company.getId(), new RoleRequest("Auditor", null, null));

        assertThatThrownBy(() -> roleService.create(company.getId(), new RoleRequest("Auditor", null, null))).isInstanceOf(ConflictException.class);
        assertThat(roleService.create(otherCompany.getId(), new RoleRequest("Auditor", null, null)).name()).isEqualTo("Auditor");
    }

    @Test
    void companyAdminCannotBeRenamedDeactivatedOrRemoved() {
        Role admin = roleRepository.save(new Role(company, "Company Admin"));

        assertThatThrownBy(() -> roleService.update(company.getId(), admin.getId(), new RoleRequest("Boss", null, null))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> roleService.update(company.getId(), admin.getId(), new RoleRequest("Company Admin", null, false))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> roleService.delete(company.getId(), admin.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void aRoleHeldByUsersCannotBeRemoved() {
        RoleResponse role = roleService.create(company.getId(), new RoleRequest("Temp", null, null));
        holder(roleRepository.findById(role.id()).orElseThrow());

        assertThatThrownBy(() -> roleService.delete(company.getId(), role.id())).isInstanceOf(ConflictException.class);
    }

    // --- features ---

    @Test
    void aCompanyListsOnlyTheFeaturesItHasRegistered() {
        Company fresh = companyRepository.save(new Company("Fresh Co " + UUID.randomUUID()));

        assertThat(featureService.list(fresh.getId())).isEmpty();

        featureService.register(fresh.getId(), List.of("order.view"));
        assertThat(featureService.list(fresh.getId())).extracting(TbacFeatureResponse::key).containsExactly("order.view");
    }

    @Test
    void registeringSkipsWhatTheCompanyHasAndNeverTouchesOnesSwitchedOff() {
        featureService.setActive(company.getId(), orderView, false);

        var result = featureService.register(company.getId(), List.of("order.view", "user.view"));

        assertThat(result.registered()).isZero();
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(featureService.list(company.getId())).filteredOn(f -> f.key().equals("order.view")).allMatch(f -> !f.active());
    }

    @Test
    void platformOnlyFeaturesCannotBeRegisteredForACompany() {
        assertThatThrownBy(() -> featureService.register(company.getId(), List.of("platform_company.view"))).isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    void theKeysThatRunTheAccessScreensCannotBeSwitchedOff() {
        UUID feature = accessControlRepository.findByKey("tbac.template_master.view").orElseThrow().getId();

        assertThatThrownBy(() -> featureService.setActive(company.getId(), feature, false)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aFeatureSwitchedOffForTheCompanyGrantsNothingEvenWhereATemplateCarriesIt() {
        TbacTemplateResponse orders = template(company, "Orders");
        templateService.addFeatures(orders.id(), Set.of(orderView));
        RoleResponse role = roleService.create(company.getId(), new RoleRequest("Orderer", null, null));
        templateService.assignRoleTemplates(role.id(), Set.of(orders.id()));
        User user = holder(roleRepository.findById(role.id()).orElseThrow());
        accessControlCacheService.invalidate(null);
        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).containsExactly("order.view");

        featureService.setActive(company.getId(), orderView, false);
        accessControlCacheService.invalidate(null);

        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).isEmpty();
    }

    // --- templates ---

    @Test
    void aTemplateGetsAKeyFromItsOwnerAndNameThatSurvivesARename() {
        TbacTemplateResponse created = template(company, "Zone Managers");
        assertThat(created.templateKey()).startsWith("c").endsWith("_zone_managers");

        TbacTemplateResponse renamed = templateService.update(created.id(), new TemplateWriteRequest("Zone Leads", null, null, null, null));

        assertThat(renamed.name()).isEqualTo("Zone Leads");
        assertThat(renamed.templateKey()).isEqualTo(created.templateKey());
    }

    @Test
    void aTemplateNameIsUniqueWithinACompany() {
        template(company, "Dupes");

        assertThatThrownBy(() -> template(company, "Dupes")).isInstanceOf(ConflictException.class);
        assertThat(template(otherCompany, "Dupes").name()).isEqualTo("Dupes");
    }

    @Test
    void featuresAreAddedAdditivelyAndOnlyFromWhatTheCompanyHasOn() {
        TbacTemplateResponse top = template(company, "Top");

        var first = templateService.addFeatures(top.id(), Set.of(orderView, userView));
        var again = templateService.addFeatures(top.id(), Set.of(orderView));

        assertThat(first.added()).isEqualTo(2);
        assertThat(again.added()).isZero();
        assertThat(again.skipped()).isEqualTo(1);

        featureService.setActive(company.getId(), orderView, false);
        TbacTemplateResponse other = template(company, "Other");
        assertThatThrownBy(() -> templateService.addFeatures(other.id(), Set.of(orderView))).isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    void removingAFeatureTakesItOffTheTemplate() {
        TbacTemplateResponse top = template(company, "Top");
        templateService.addFeatures(top.id(), Set.of(orderView, userView));

        templateService.removeFeature(top.id(), orderView);

        assertThat(templateService.get(top.id()).directFeatureCount()).isEqualTo(1);
    }

    @Test
    void effectiveFeaturesSplitWhatIsDirectFromWhatIsInherited() {
        TbacTemplateResponse base = template(company, "Base");
        templateService.addFeatures(base.id(), Set.of(orderView));
        TbacTemplateResponse top = template(company, "Top");
        templateService.addFeatures(top.id(), Set.of(userView));
        templateService.addReferences(top.id(), Set.of(base.id()));

        EffectiveFeaturesResponse effective = templateService.effectiveFeatures(top.id());

        assertThat(effective.direct()).extracting(EffectiveFeaturesResponse.Entry::key).containsExactly("user.view");
        assertThat(effective.inherited()).extracting(EffectiveFeaturesResponse.Entry::key).containsExactly("order.view");
        assertThat(templateService.get(top.id()).effectiveFeatureCount()).isEqualTo(2);
    }

    @Test
    void addingIncludesJudgesEachCandidateAlone() {
        TbacTemplateResponse top = template(company, "Top");
        TbacTemplateResponse good = template(company, "Good");
        TbacTemplateResponse foreign = template(otherCompany, "Foreign");

        AddReferencesResponse result = templateService.addReferences(top.id(), Set.of(good.id(), foreign.id(), top.id()));

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.totalRequested()).isEqualTo(3);
        assertThat(result.errors()).hasSize(2);
        assertThat(templateService.references(top.id())).containsExactly(good.id());
        assertThat(templateService.addReferences(top.id(), Set.of(good.id())).errors().get(0)).contains("already included");
    }

    @Test
    void anIncludeThatWouldLoopIsRefusedWithoutBlockingTheRest() {
        TbacTemplateResponse a = template(company, "A");
        TbacTemplateResponse b = template(company, "B");
        templateService.addReferences(b.id(), Set.of(a.id()));

        AddReferencesResponse result = templateService.addReferences(a.id(), Set.of(b.id()));

        assertThat(result.added()).isZero();
        assertThat(result.errors().get(0)).contains("loop");
    }

    @Test
    void anIncludeCanBeRemoved() {
        TbacTemplateResponse base = template(company, "Base");
        TbacTemplateResponse top = template(company, "Top");
        templateService.addReferences(top.id(), Set.of(base.id()));

        templateService.removeReference(top.id(), base.id());

        assertThat(templateService.references(top.id())).isEmpty();
    }

    @Test
    void aTemplateIsDeactivatedNotDeleted() {
        TbacTemplateResponse created = template(company, "Retiring");

        TbacTemplateResponse retired = templateService.deactivate(created.id());

        assertThat(retired.active()).isFalse();
        assertThat(templateService.get(created.id()).name()).isEqualTo("Retiring");
    }

    @Test
    void aCompanysListHoldsItsOwnTemplatesAndTheSystemOnes() {
        template(company, "Mine");
        template(otherCompany, "Theirs");
        TbacTemplateResponse system = template(null, "Shared " + UUID.randomUUID());

        List<TbacTemplateResponse> seen = templateService.list(company.getId());

        assertThat(seen).extracting(TbacTemplateResponse::name).contains("Mine", system.name()).doesNotContain("Theirs");
        assertThat(seen).filteredOn(TbacTemplateResponse::isSystem).extracting(TbacTemplateResponse::name).contains(system.name());
    }

    // --- roles and users carry templates ---

    @Test
    void aRoleGrantsOnlyWhatItsTemplatesGrant() {
        TbacTemplateResponse orders = template(company, "Orders");
        templateService.addFeatures(orders.id(), Set.of(orderView));
        RoleResponse role = roleService.create(company.getId(), new RoleRequest("Orderer", null, null));
        User user = holder(roleRepository.findById(role.id()).orElseThrow());

        templateService.assignRoleTemplates(role.id(), Set.of(orders.id()));
        accessControlCacheService.invalidate(null);
        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).containsExactly("order.view");

        templateService.removeRoleTemplate(role.id(), orders.id());
        accessControlCacheService.invalidate(null);
        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).isEmpty();
    }

    @Test
    void aCompanyCannotMapAnotherCompanysTemplateToItsRole() {
        TbacTemplateResponse foreign = template(otherCompany, "Foreign");
        RoleResponse role = roleService.create(company.getId(), new RoleRequest("Mapper", null, null));

        assertThatThrownBy(() -> templateService.assignRoleTemplates(role.id(), Set.of(foreign.id()))).isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    void aUserCarriesTemplatesOnTopOfTheirRole() {
        TbacTemplateResponse billing = template(company, "Billing");
        templateService.addFeatures(billing.id(), Set.of(userView));
        User user = holder(roleRepository.save(new Role(company, "Plain " + UUID.randomUUID())));

        templateService.assignUserTemplates(user.getId(), Set.of(billing.id()));
        accessControlCacheService.invalidate(null);

        assertThat(templateService.userTemplates(user.getId())).extracting(TbacTemplateResponse::name).containsExactly("Billing");
        assertThat(accessControlCacheService.resolveEffectiveKeys(user)).containsExactly("user.view");
    }

    @Test
    void bulkAssignAddsTemplatesToEachUserAndIsolatesFailures() {
        TbacTemplateResponse orders = template(company, "Orders");
        Role member = roleRepository.save(new Role(company, "Member " + UUID.randomUUID()));
        User one = holder(member);
        User foreignUser = userRepository.save(new User(otherCompany, roleRepository.save(new Role(otherCompany, "M " + UUID.randomUUID())), "Two", "two-" + UUID.randomUUID() + "@x.com", "h"));

        BulkAssignUserTemplatesResponse first =
                templateService.bulkAssignUserTemplates(Set.of(one.getId(), foreignUser.getId(), UUID.randomUUID()), Set.of(orders.id()));
        BulkAssignUserTemplatesResponse again = templateService.bulkAssignUserTemplates(Set.of(one.getId()), Set.of(orders.id()));

        assertThat(first.totalUsersProcessed()).isEqualTo(3);
        assertThat(first.successfulUsers()).isEqualTo(2);
        assertThat(first.failedUsers()).isEqualTo(1);
        assertThat(first.userResults()).extracting(BulkAssignUserTemplatesResponse.UserResult::status).containsExactlyInAnyOrder("success", "success", "failed");
        assertThat(again.totalAssignmentsSkipped()).isEqualTo(1);
        assertThat(again.totalAssignmentsCreated()).isZero();
    }
}
