package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * The visibility rule on a small company:
 * <pre>
 *   North (zone) ── Punjab (region) ── Ludhiana HQ
 *   South (zone)
 * </pre>
 * Kavya heads North; Ravi heads Punjab and reports to Kavya; Aditi and Rahul are MRs in Ludhiana
 * HQ; Aditi reports to Sanjay, an ABM who also sits in Ludhiana HQ. Redis is an in-memory map.
 */
class VisibilityServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final OrgUnitRepository orgUnits = mock(OrgUnitRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Map<String, String> store = new HashMap<>();
    private final VisibilityService service = new VisibilityService(users, orgUnits, redis);

    private final Company company = new Company();
    private final OrgUnit north = unit("North", null);
    private final OrgUnit punjab = unit("Punjab", north);
    private final OrgUnit ludhiana = unit("Ludhiana HQ", punjab);
    private final OrgUnit south = unit("South", null);
    private final User kavya = user("Kavya", north);
    private final User ravi = user("Ravi", punjab);
    private final User sanjay = user("Sanjay", ludhiana);
    private final User aditi = user("Aditi", ludhiana);
    private final User rahul = user("Rahul", ludhiana);

    @BeforeEach
    void setUp() {
        company.setId(UUID.randomUUID());
        for (User u : List.of(kavya, ravi, sanjay, aditi, rahul)) {
            u.setCompany(company);
        }
        when(orgUnits.findDescendantIds(north.getId())).thenReturn(List.of(punjab.getId(), ludhiana.getId()));
        when(orgUnits.findDescendantIds(punjab.getId())).thenReturn(List.of(ludhiana.getId()));
        when(orgUnits.findDescendantIds(ludhiana.getId())).thenReturn(List.of());
        when(orgUnits.findSelfAndDescendants(south.getId())).thenReturn(List.of(south));
        when(users.findReportingLineIds(any())).thenReturn(List.of());
        when(users.findReportingLineIds(kavya.getId())).thenReturn(List.of(ravi.getId()));
        when(users.findReportingLineIds(sanjay.getId())).thenReturn(List.of(aditi.getId()));

        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.increment(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            long next = Long.parseLong(store.getOrDefault(key, "0")) + 1;
            store.put(key, Long.toString(next));
            return next;
        });
    }

    @Test
    void anMrAtTheBottomWithNobodyReportingToThemSeesOnlyTheirOwnRecords() {
        Visibility v = service.resolve(rahul);
        assertThat(v.ownRecordsOnly()).isTrue();
        assertThat(v.canSee(rahul, ludhiana)).isTrue();
        assertThat(v.canSee(aditi, ludhiana)).as("a peer in the same HQ").isFalse();
    }

    @Test
    void aManagerInTheSameUnitSeesTheirReportsButNotOtherPeers() {
        Visibility v = service.resolve(sanjay);
        assertThat(v.canSee(aditi, ludhiana)).as("Aditi reports to Sanjay").isTrue();
        assertThat(v.canSee(rahul, ludhiana)).as("Rahul doesn't").isFalse();
    }

    @Test
    void aRegionalHeadSeesEverythingBelowTheirRegion() {
        Visibility v = service.resolve(ravi);
        assertThat(v.canSee(rahul, ludhiana)).isTrue();
        assertThat(v.canSee(aditi, ludhiana)).isTrue();
        assertThat(v.canSee(kavya, north)).as("above them").isFalse();
        assertThat(v.canSee(null, south)).isFalse();
    }

    @Test
    void aZoneHeadSeesTheirZoneAndTheirReportsRecordsInTheirOwnUnit() {
        Visibility v = service.resolve(kavya);
        assertThat(v.canSee(rahul, ludhiana)).isTrue();
        assertThat(v.canSee(ravi, punjab)).isTrue();
        // A record Ravi placed while sitting in North itself is still visible through the reporting line.
        assertThat(v.canSee(ravi, north)).isTrue();
        assertThat(v.canSee(null, south)).isFalse();
    }

    @Test
    void aCoveredUnitIsSeenEntirelyIncludingItsOwnRecords() {
        ravi.getExtraOrgUnits().add(south);
        Visibility v = service.resolve(ravi);
        assertThat(v.canSee(null, south)).isTrue();
    }

    @Test
    void companyWideAndPlatformStaffSeeEverything() {
        rahul.setCompanyWide(true);
        assertThat(service.resolve(rahul).canSee(kavya, north)).isTrue();

        User platform = user("Platform", null);
        platform.setCompany(null);
        assertThat(service.resolve(platform).companyWide()).isTrue();
    }

    @Test
    void theResultIsCachedUntilTheCompanysStructureChanges() {
        Visibility first = service.resolve(sanjay);
        Visibility cached = service.resolve(sanjay);
        assertThat(cached).isEqualTo(first);
        verify(users, times(1)).findReportingLineIds(sanjay.getId());

        // Rahul now reports to Sanjay too; the change is published, and the next resolve sees it.
        when(users.findReportingLineIds(sanjay.getId())).thenReturn(List.of(aditi.getId(), rahul.getId()));
        service.onCompanyStructureChanged(new CompanyStructureChangedEvent(company.getId()));
        assertThat(service.resolve(sanjay).canSee(rahul, ludhiana)).isTrue();
    }

    @Test
    void withoutRedisVisibilityIsStillComputed() {
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        assertThat(service.resolve(sanjay).canSee(aditi, ludhiana)).isTrue();
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void queryCollectionsAreNeverEmpty() {
        Visibility v = service.resolve(rahul);
        assertThat(v.peopleForQuery()).containsExactly(rahul.getId());
        assertThat(v.orgUnitsForQuery()).hasSize(1).doesNotContain(ludhiana.getId());
        assertThat(service.resolve(kavya).orgUnitsForQuery()).isEqualTo(Set.of(punjab.getId(), ludhiana.getId()));
    }

    private static OrgUnit unit(String name, OrgUnit parent) {
        OrgUnit unit = new OrgUnit();
        unit.setId(UUID.randomUUID());
        unit.setName(name);
        unit.setParent(parent);
        return unit;
    }

    private static User user(String name, OrgUnit orgUnit) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setName(name);
        u.setOrgUnit(orgUnit);
        return u;
    }
}
