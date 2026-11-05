package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.dto.OrderDesignResponse;
import com.wrapzzo.backend.dto.PendingArtworkApprovalResponse;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyFeature;
import com.wrapzzo.backend.model.ArtworkApprovalScope;
import com.wrapzzo.backend.model.CompanyArtworkApprovalStep;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignApproval;
import com.wrapzzo.backend.model.OrderDesignDecision;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.OrderDesignApprovalRepository;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.CompanyArtworkApprovalStepRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The multi-level artwork chain: MR -> their manager -> that manager's manager ..., up to the
 * company's artwork_approval_levels, with order.design.final holders able to cover a level.
 * Repositories are mocked; the approval trail is kept in a list the mocks read back.
 */
class OrderDesignServiceTest {

    private final OrderDesignRepository designs = mock(OrderDesignRepository.class);
    private final OrderDesignApprovalRepository approvals = mock(OrderDesignApprovalRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CompanyFeatureFlagService flags = mock(CompanyFeatureFlagService.class);
    private final AccessControlCacheService keys = mock(AccessControlCacheService.class);
    private final VisibilityService visibility = mock(VisibilityService.class);
    private final FileStorageService files = mock(FileStorageService.class);

    private final CompanyArtworkApprovalStepRepository steps = mock(CompanyArtworkApprovalStepRepository.class);
    private final OrgUnitRepository orgUnits = mock(OrgUnitRepository.class);
    private final org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);

    private final OrderDesignService service = new OrderDesignService(designs, approvals, orders,
            mock(QuickGiftRequestRepository.class), steps, orgUnits, mock(VendorRepository.class), users, files, visibility, flags, keys,
            events);

    private final List<OrderDesignApproval> trail = new ArrayList<>();
    private final Company company = new Company();
    // MR Aditi -> ABM Sanjay -> AM Ravi -> RM Kavya; Neha holds order.design.final, outside the line.
    private final User kavya = user("Kavya", null);
    private final User ravi = user("Ravi", kavya);
    private final User sanjay = user("Sanjay", ravi);
    private final User aditi = user("Aditi", sanjay);
    private final User neha = user("Neha", null);
    private final User outsider = user("Priya", null);
    private Order order;
    private OrderDesign design;

    @BeforeEach
    void setUp() {
        company.setId(UUID.randomUUID());
        company.setArtworkApprovalLevels(3);
        for (User u : List.of(kavya, ravi, sanjay, aditi, neha, outsider)) {
            u.setCompany(company);
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
            when(keys.resolveEffectiveKeys(u)).thenReturn(u == neha ? Set.of(OrderDesignService.FINAL_APPROVAL_KEY) : Set.of("artwork.review", "approval.decide"));
            when(visibility.resolve(u)).thenReturn(Visibility.everything(u.getId()));
        }
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setCompany(company);
        order.setCreatedBy(aditi);
        order.setOwner(aditi);
        Vendor vendor = new Vendor();
        vendor.setName("PrintCo");
        design = new OrderDesign(order, 1, "key", "proof.pdf", vendor);
        design.setId(UUID.randomUUID());

        when(designs.findById(design.getId())).thenReturn(Optional.of(design));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(flags.isEnabled(company.getId(), CompanyFeature.MARKETING_DESIGN_APPROVAL)).thenReturn(true);
        when(files.publicUrl(anyString())).thenReturn("https://files/proof.pdf");
        when(approvals.save(any(OrderDesignApproval.class))).thenAnswer(inv -> {
            trail.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(approvals.existsByDesign_IdAndDecidedBy_Id(eq(design.getId()), any()))
                .thenAnswer(inv -> trail.stream().anyMatch(a -> a.getDecidedBy().getId().equals(inv.getArgument(1))));
        when(approvals.findByDesign_IdInOrderByDecidedAtAsc(anyCollection())).thenAnswer(inv -> List.copyOf(trail));
        when(designs.findByStatusAndOrder_Company_IdOrderByUpdatedAtAsc(OrderDesignStatus.PENDING_FINAL_APPROVAL, company.getId()))
                .thenAnswer(inv -> design.getStatus() == OrderDesignStatus.PENDING_FINAL_APPROVAL ? List.of(design) : List.of());
    }

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void climbsEveryLevelOfTheReportingLineBeforeItIsApproved() {
        OrderDesignResponse afterMr = approveAs(aditi);
        assertThat(afterMr.status()).isEqualTo(OrderDesignStatus.PENDING_FINAL_APPROVAL);
        assertThat(afterMr.approvalLevel()).isEqualTo(1);
        assertThat(afterMr.approvalLevels()).isEqualTo(3);
        assertThat(afterMr.waitingOnName()).isEqualTo("Sanjay");

        assertThat(approveAs(sanjay).waitingOnName()).isEqualTo("Ravi");
        assertThat(design.getApprovalLevel()).isEqualTo(2);
        assertThat(approveAs(ravi).waitingOnName()).isEqualTo("Kavya");

        OrderDesignResponse done = approveAs(kavya);
        assertThat(done.status()).isEqualTo(OrderDesignStatus.APPROVED);
        assertThat(done.reviewedByUserName()).isEqualTo("Kavya");
        assertThat(done.approvals()).extracting(step -> step.level() + ":" + step.decidedByName())
                .containsExactly("0:Aditi", "1:Sanjay", "2:Ravi", "3:Kavya");
    }

    @Test
    void stopsAtTheConfiguredNumberOfLevels() {
        company.setArtworkApprovalLevels(2);
        approveAs(aditi);
        approveAs(sanjay);
        assertThat(approveAs(ravi).status()).isEqualTo(OrderDesignStatus.APPROVED);
    }

    @Test
    void aShortReportingLineEndsAtItsTop() {
        sanjay.setReportsTo(null);
        OrderDesignResponse afterMr = approveAs(aditi);
        assertThat(afterMr.approvalLevels()).isEqualTo(1);
        assertThat(approveAs(sanjay).status()).isEqualTo(OrderDesignStatus.APPROVED);
    }

    @Test
    void withNoOneAboveTheMrAFinalApprovalHolderSignsIt() {
        aditi.setReportsTo(null);
        OrderDesignResponse afterMr = approveAs(aditi);
        assertThat(afterMr.approvalLevels()).isEqualTo(1);
        assertThat(afterMr.waitingOnName()).isNull();
        assertThatThrownBy(() -> approveAs(outsider)).isInstanceOf(AccessDeniedException.class);
        assertThat(approveAs(neha).status()).isEqualTo(OrderDesignStatus.APPROVED);
    }

    @Test
    void aFinalApprovalHolderCoversALevelAndTheTrailSaysForWhom() {
        approveAs(aditi);
        as(neha);
        List<PendingArtworkApprovalResponse> queue = service.listPendingFinalApproval();
        assertThat(queue).singleElement().satisfies(item -> {
            assertThat(item.covering()).isTrue();
            assertThat(item.waitingOnName()).isEqualTo("Sanjay");
        });

        OrderDesignResponse afterCover = approveAs(neha);
        assertThat(afterCover.approvalLevel()).isEqualTo(2);
        assertThat(afterCover.approvals().get(1).decidedByName()).isEqualTo("Neha");
        assertThat(afterCover.approvals().get(1).onBehalfOfName()).isEqualTo("Sanjay");
        // One signature per person: Neha can't also sign level 2.
        assertThatThrownBy(() -> approveAs(neha)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyTheManagerTheLevelWaitsOnSeesItInTheirQueue() {
        approveAs(aditi);
        as(sanjay);
        assertThat(service.listPendingFinalApproval()).singleElement().satisfies(item -> assertThat(item.covering()).isFalse());
        as(ravi);
        assertThat(service.listPendingFinalApproval()).isEmpty();
        assertThatThrownBy(() -> approveAs(ravi)).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Sanjay");
        as(aditi);
        assertThat(service.listPendingFinalApproval()).isEmpty();
    }

    @Test
    void theMrsFirstReviewAndTheManagerLevelsAreSeparatePermissions() {
        // An MR with only the cycle permission cannot do the first review...
        when(keys.resolveEffectiveKeys(aditi)).thenReturn(Set.of("approval.decide"));
        assertThatThrownBy(() -> approveAs(aditi)).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Review artwork");

        // ...and with only the review permission she does it, but is no approver up the cycle.
        when(keys.resolveEffectiveKeys(aditi)).thenReturn(Set.of("artwork.review"));
        approveAs(aditi);

        // The manager the level waits on, holding only the review permission, cannot approve at his level.
        when(keys.resolveEffectiveKeys(sanjay)).thenReturn(Set.of("artwork.review"));
        assertThatThrownBy(() -> approveAs(sanjay)).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Approve artwork");
        as(sanjay);
        assertThat(service.listPendingFinalApproval()).isEmpty();

        // With the cycle permission he can.
        when(keys.resolveEffectiveKeys(sanjay)).thenReturn(Set.of("approval.decide"));
        assertThat(approveAs(sanjay).waitingOnName()).isEqualTo("Ravi");
    }

    @Test
    void theVendorIsToldOnlyOfTheFinalApprovalAndOfAnySendBack_notOfTheLevelsOnTheWayUp() {
        approveAs(aditi);
        approveAs(sanjay);
        approveAs(ravi);
        org.mockito.Mockito.verify(events, org.mockito.Mockito.never()).publishEvent(org.mockito.ArgumentMatchers.any(VendorProofDecidedEvent.class));

        approveAs(kavya);
        org.mockito.Mockito.verify(events).publishEvent(new VendorProofDecidedEvent(design.getId(), true));
    }

    @Test
    void aSendBackFromAnyLevelTellsTheVendor() {
        approveAs(aditi);
        as(sanjay);
        service.reject(order.getId(), design.getId(), "Wrong clinic address");
        org.mockito.Mockito.verify(events).publishEvent(new VendorProofDecidedEvent(design.getId(), false));
    }

    @Test
    void anyLevelCanSendItBackToTheVendor() {
        approveAs(aditi);
        approveAs(sanjay);
        as(ravi);
        OrderDesignResponse rejected = service.reject(order.getId(), design.getId(), "Wrong clinic address");
        assertThat(rejected.status()).isEqualTo(OrderDesignStatus.REJECTED);
        assertThat(rejected.reviewComment()).isEqualTo("Wrong clinic address");
        assertThat(trail).last().satisfies(step -> {
            assertThat(step.getLevel()).isEqualTo(2);
            assertThat(step.getDecision()).isEqualTo(OrderDesignDecision.REJECTED);
        });
    }

    @Test
    void withTheFeatureOffTheMrsApprovalIsFinal() {
        when(flags.isEnabled(company.getId(), CompanyFeature.MARKETING_DESIGN_APPROVAL)).thenReturn(false);
        OrderDesignResponse response = approveAs(aditi);
        assertThat(response.status()).isEqualTo(OrderDesignStatus.APPROVED);
        assertThat(response.approvalLevel()).isNull();
        assertThat(trail).singleElement().satisfies(step -> assertThat(step.getLevel()).isZero());
    }

    /**
     * AM -> RM (in the AM's line) -> an HO of the AM's division. The ZM between them only watches: no step is his, so he
     * can neither see it in his queue nor sign it. Any one HO of the division decides; an HO of another division never can.
     */
    @Test
    void anAmsProofGoesToTheirRmThenToAnHoOfTheirDivisionWhileTheZmOnlyWatches() {
        Role amRole = role("AM"), rmRole = role("RM"), zmRole = role("ZM"), hoRole = role("HO");
        OrgUnit summit = unit("Summit", null), maxis = unit("Maxis", null);
        OrgUnit zone = unit("Kolkata", summit), area = unit("Howrah", zone);
        User zm = user("Zara ZM", null), rm = user("Ravi RM", zm), am = user("Aditi AM", rm);
        User hoOne = user("Hari HO", null), hoTwo = user("Hema HO", null), hoElsewhere = user("Omar HO", null);
        zm.setRole(zmRole);
        rm.setRole(rmRole);
        am.setRole(amRole);
        am.setOrgUnit(area);
        for (User ho : List.of(hoOne, hoTwo)) {
            ho.setRole(hoRole);
            ho.setOrgUnit(summit);
        }
        hoElsewhere.setRole(hoRole);
        hoElsewhere.setOrgUnit(maxis);
        for (User u : List.of(zm, rm, am, hoOne, hoTwo, hoElsewhere)) {
            u.setCompany(company);
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
            when(keys.resolveEffectiveKeys(u)).thenReturn(Set.of("artwork.review", "approval.decide"));
            when(visibility.resolve(u)).thenReturn(Visibility.everything(u.getId()));
        }
        when(orgUnits.findById(summit.getId())).thenReturn(Optional.of(summit));
        when(users.findByCompanyIdAndRoleId(company.getId(), hoRole.getId())).thenReturn(List.of(hoOne, hoTwo, hoElsewhere));
        when(steps.findByCompany_IdOrderByStepNoAsc(company.getId())).thenReturn(List.of(
                new CompanyArtworkApprovalStep(company, 1, rmRole, ArtworkApprovalScope.REPORTING_LINE),
                new CompanyArtworkApprovalStep(company, 2, hoRole, ArtworkApprovalScope.DIVISION)));
        order.setOwner(am);
        order.setCreatedBy(am);

        OrderDesignResponse afterAm = approveAs(am);
        assertThat(afterAm.status()).isEqualTo(OrderDesignStatus.PENDING_FINAL_APPROVAL);
        assertThat(afterAm.approvalLevels()).isEqualTo(2);
        assertThat(afterAm.waitingOnName()).isEqualTo("Ravi RM");

        // The ZM watches: not his to sign, and not in his queue.
        assertThatThrownBy(() -> approveAs(zm)).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Ravi RM");
        assertThat(service.listPendingFinalApproval()).isEmpty();
        as(hoOne);
        assertThat(service.listPendingFinalApproval()).isEmpty();

        OrderDesignResponse afterRm = approveAs(rm);
        assertThat(afterRm.approvalLevel()).isEqualTo(2);
        assertThat(afterRm.waitingOnName()).isEqualTo("HO of Summit");

        // Both HOs of the division see it as theirs; the other division's HO and the ZM do not.
        for (User ho : List.of(hoOne, hoTwo)) {
            as(ho);
            assertThat(service.listPendingFinalApproval()).singleElement().satisfies(item -> {
                assertThat(item.covering()).isFalse();
                assertThat(item.waitingOnName()).isEqualTo("HO of Summit");
            });
        }
        as(hoElsewhere);
        assertThat(service.listPendingFinalApproval()).isEmpty();
        assertThatThrownBy(() -> approveAs(hoElsewhere)).isInstanceOf(AccessDeniedException.class).hasMessageContaining("HO of Summit");

        // The first HO to act decides.
        OrderDesignResponse done = approveAs(hoTwo);
        assertThat(done.status()).isEqualTo(OrderDesignStatus.APPROVED);
        assertThat(done.reviewedByUserName()).isEqualTo("Hema HO");
        assertThat(done.approvals()).extracting(step -> step.level() + ":" + step.decidedByName())
                .containsExactly("0:Aditi AM", "1:Ravi RM", "2:Hema HO");
        assertThat(done.approvals().get(2).onBehalfOfName()).isNull();
    }

    @Test
    void anHoCanSendTheProofBackToTheVendor() {
        Role rmRole = role("RM"), hoRole = role("HO");
        OrgUnit summit = unit("Summit", null);
        User rm = user("Ravi RM", null), am = user("Aditi AM", rm), ho = user("Hari HO", null);
        rm.setRole(rmRole);
        ho.setRole(hoRole);
        ho.setOrgUnit(summit);
        am.setOrgUnit(summit);
        for (User u : List.of(rm, am, ho)) {
            u.setCompany(company);
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
            when(keys.resolveEffectiveKeys(u)).thenReturn(Set.of("artwork.review", "approval.decide"));
            when(visibility.resolve(u)).thenReturn(Visibility.everything(u.getId()));
        }
        when(users.findByCompanyIdAndRoleId(company.getId(), hoRole.getId())).thenReturn(List.of(ho));
        when(steps.findByCompany_IdOrderByStepNoAsc(company.getId())).thenReturn(List.of(
                new CompanyArtworkApprovalStep(company, 1, rmRole, ArtworkApprovalScope.REPORTING_LINE),
                new CompanyArtworkApprovalStep(company, 2, hoRole, ArtworkApprovalScope.DIVISION)));
        order.setOwner(am);
        approveAs(am);
        approveAs(rm);

        as(ho);
        OrderDesignResponse rejected = service.reject(order.getId(), design.getId(), "Logo is off-brand");
        assertThat(rejected.status()).isEqualTo(OrderDesignStatus.REJECTED);
        assertThat(rejected.reviewComment()).isEqualTo("Logo is off-brand");
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(name);
        return role;
    }

    private static OrgUnit unit(String name, OrgUnit parent) {
        OrgUnit unit = new OrgUnit();
        unit.setId(UUID.randomUUID());
        unit.setName(name);
        unit.setParent(parent);
        if (parent != null) {
            unit.getAncestorIds().addAll(parent.getAncestorIds());
            unit.getAncestorIds().add(parent.getId());
        }
        return unit;
    }

    private OrderDesignResponse approveAs(User reviewer) {
        as(reviewer);
        return service.approve(order.getId(), design.getId());
    }

    private static void as(User caller) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(caller.getId(), null, List.of()));
    }

    private static User user(String name, User reportsTo) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setName(name);
        u.setReportsTo(reportsTo);
        return u;
    }

    /**
     * open-in-view is off, so a service method that touches a lazy association outside a transaction fails at run
     * time with a server error -- and the integration tests, which run inside one big transaction, never see it.
     * Every public method here reads such associations, so each must open its own.
     */
    @Test
    void everyPublicMethodRunsInATransaction() {
        for (java.lang.reflect.Method method : OrderDesignService.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers()) && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                org.assertj.core.api.Assertions.assertThat(method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                        .as(method.getName() + " is @Transactional")
                        .isTrue();
            }
        }
    }
}
