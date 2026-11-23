package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.DashboardInsightsResponse;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.service.DashboardQueries.Reach;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The part of the dashboard that changes with who is looking: a person who sees only their own records gets a personal view (their
 * doctors, their orders); anyone who sees more also gets one row per person below them. Everything respects the same visibility as
 * the Orders list.
 */
@Service
@RequiredArgsConstructor
public class DashboardInsightsService {

    static final Set<String> RANGES = Set.of("7d", "30d", "90d", "year", "all");
    private static final int TREND_WEEKS = 12;
    private static final int TEAM_LIMIT = 200;

    private final UserRepository userRepository;
    private final VisibilityService visibilityService;
    private final DashboardInsightsQueries queries;
    private final PricePolicy pricePolicy;
    private final VendorIdentityPolicy vendorIdentityPolicy;
    private final DashboardCache cache;

    record Window(String range, Instant from, Instant to) {
    }

    static Window window(String requestedRange) {
        String range = RANGES.contains(requestedRange) ? requestedRange : "30d";
        Instant to = Instant.now().plusSeconds(60);
        Instant from = switch (range) {
            case "7d" -> to.minus(Duration.ofDays(7));
            case "90d" -> to.minus(Duration.ofDays(90));
            case "year" -> LocalDate.now(ZoneOffset.UTC).withDayOfYear(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            case "all" -> Instant.parse("2000-01-01T00:00:00Z");
            default -> to.minus(Duration.ofDays(30));
        };
        return new Window(range, from, to);
    }

    /** The viewer's own doctors who have had no PEL requested in the period -- who to follow up with. */
    @Transactional(readOnly = true)
    public com.wrapzzo.backend.dto.DashboardDrilldownResponse uncoveredDoctors(String requestedRange, int limit) {
        Window w = window(requestedRange);
        var rows = queries.uncoveredDoctors(CurrentUser.id(), w.from(), w.to(), limit).stream()
                .map(r -> new com.wrapzzo.backend.dto.DashboardDrilldownResponse.Row(
                        null, DoctorLabel.of((String) r[3], (String) r[0]), r[1] == null ? "" : r[1].toString(), null, toInstant(r[2]), null, null))
                .toList();
        return new com.wrapzzo.backend.dto.DashboardDrilldownResponse("Doctors with no PEL yet", rows);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        return value instanceof java.time.OffsetDateTime o ? o.toInstant() : (Instant) value;
    }

    @Transactional(readOnly = true)
    public DashboardInsightsResponse insights(String requestedRange) {
        Window w = window(requestedRange);
        return cache.get("insights:" + CurrentUser.id() + ":" + w.range(), () -> compute(w));
    }

    private DashboardInsightsResponse compute(Window w) {
        String range = w.range();
        User user = userRepository.findById(CurrentUser.id()).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + CurrentUser.id()));
        Visibility visibility = visibilityService.resolve(user);
        boolean personal = visibility.ownRecordsOnly();
        Reach reach = Reach.of(user.getCompany().getId(), visibility);

        Instant to = w.to();
        Instant from = w.from();
        boolean all = range.equals("all");
        Instant previousFrom = from.minus(Duration.between(from, to));

        LocalDate firstMonday = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(TREND_WEEKS - 1L);
        // One query for the people in view (a personal view is just the viewer); the coverage total, the size and the table all come from it.
        List<DashboardInsightsResponse.TeamMember> people = queries.people(reach, user.getId(), personal, from, to);
        return new DashboardInsightsResponse(
                personal ? "PERSONAL" : "TEAM",
                range,
                from,
                to,
                queries.period(reach, from, to),
                all ? null : queries.period(reach, previousFrom, from),
                DashboardInsightsQueries.coverageOf(people),
                queries.stages(reach),
                queries.rejectedRequests(reach, from, to),
                queries.weekly(reach, firstMonday, TREND_WEEKS),
                personal ? 0 : people.size(),
                personal ? List.of() : people.stream().limit(TEAM_LIMIT).toList(),
                pricePolicy.canSee() ? queries.spend(reach, from, to, all ? null : previousFrom) : null,
                vendorIdentityPolicy.canSee() ? queries.vendors(reach, from, to) : null);
    }
}
