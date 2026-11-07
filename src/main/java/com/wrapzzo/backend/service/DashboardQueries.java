package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftStatus;
import com.wrapzzo.backend.model.Submission;
import com.wrapzzo.backend.model.SubmissionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Everything the dashboard counts, summed in the database over what the viewer may see -- never by loading the records. Each query
 * carries the same reach rule as the Orders list (the viewer's own, their team's, their org units'; everyone's when company-wide),
 * so a figure and the rows behind it always agree. Lists that feed the screen (recent orders, the activity feed, drill-downs) take
 * only the newest few.
 */
@Component
class DashboardQueries {

    /** Who the numbers are for: a company and the part of it the viewer reaches. */
    record Reach(UUID companyId, boolean companyWide, Collection<UUID> people, Collection<UUID> orgUnits) {
        static Reach of(UUID companyId, Visibility visibility) {
            return new Reach(companyId, visibility.companyWide(), visibility.peopleForQuery(), visibility.orgUnitsForQuery());
        }
    }

    private static final String ORDERS = " from Order o where o.company.id = :companyId and (:companyWide = true or o.owner.id in :people or o.orgUnit.id in :orgUnits)";
    private static final String QUICK_GIFTS =
            " from QuickGiftRequest q where q.company.id = :companyId and (:companyWide = true or q.requestedBy.id in :people or q.orgUnit.id in :orgUnits)";
    private static final String SUBMISSIONS =
            " from Submission s where s.company.id = :companyId and (:companyWide = true or s.submittedBy.id in :people or s.orgUnit.id in :orgUnits)";
    /** Native twin of ORDERS, for the figures JPQL cannot express (phone digits, week buckets). */
    private static final String ORDERS_SQL =
            " from orders o where o.company_id = :companyId and (:companyWide = true or o.owner_id in (:people) or o.org_unit_id in (:orgUnits))";
    private static final String QUICK_GIFTS_SQL =
            " from quick_gift_requests q where q.company_id = :companyId and (:companyWide = true or q.requested_by in (:people) or q.org_unit_id in (:orgUnits))";
    private static final String SUBMISSIONS_SQL =
            " from submissions s where s.company_id = :companyId and (:companyWide = true or s.submitted_by in (:people) or s.org_unit_id in (:orgUnits))";

    /** A PEL's doctor: one per phone number (its digits), or by name when there is no phone -- so a doctor sent PELs twice counts once. */
    private static final String DOCTOR_KEY =
            "coalesce('p:' || nullif(regexp_replace(coalesce(o.recipient_phone, ''), '\\D', '', 'g'), ''), 'n:' || lower(trim(coalesce(o.recipient_name, ''))))";
    private static final String HAS_DOCTOR =
            " and ((o.recipient_phone is not null and trim(o.recipient_phone) <> '') or (o.recipient_name is not null and trim(o.recipient_name) <> ''))";

    @PersistenceContext private EntityManager em;

    // --- orders ---

    Map<OrderStatus, Long> ordersByStatus(Reach reach) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (Object[] row : rows("select o.status, count(o)" + ORDERS + " group by o.status", reach)) {
            counts.put((OrderStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    Map<String, Long> ordersBySource(Reach reach) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : rows("select o.source, count(o)" + ORDERS + " group by o.source", reach)) {
            counts.put(row[0].toString(), (Long) row[1]);
        }
        return counts;
    }

    /** PELs (units) on live orders, or on delivered ones. */
    long pels(Reach reach, boolean deliveredOnly) {
        String where = deliveredOnly ? " and o.status = com.wrapzzo.backend.model.OrderStatus.DELIVERED" : " and o.status <> com.wrapzzo.backend.model.OrderStatus.CANCELLED";
        Number sum = (Number) bind(em.createQuery("select coalesce(sum(o.quantity), 0)" + ORDERS + where), reach).getSingleResult();
        return sum.longValue();
    }

    long doctors(Reach reach, boolean deliveredOnly) {
        String status = deliveredOnly ? " and o.status = 'DELIVERED'" : " and o.status <> 'CANCELLED'";
        Number count = (Number) bind(em.createNativeQuery("select count(distinct " + DOCTOR_KEY + ")" + ORDERS_SQL + status + HAS_DOCTOR), reach).getSingleResult();
        return count.longValue();
    }

    /** (gift name, orders) most first. */
    List<Object[]> topGifts(Reach reach, int limit) {
        return limited(rows("select g.name, count(o)" + ORDERS.replace(" from Order o", " from Order o join o.gift g")
                + " group by g.name order by count(o) desc", reach), limit);
    }

    /** (campaign id, orders) most first. */
    List<Object[]> topCampaigns(Reach reach, int limit) {
        return limited(rows("select c.id, count(o)" + ORDERS.replace(" from Order o", " from Order o join o.campaign c")
                + " group by c.id order by count(o) desc", reach), limit);
    }

    /**
     * (department, orders) most first. The unit the order was raised from; older ones fall back to the submitter's or the requester's
     * current unit.
     */
    List<Object[]> topDepartments(Reach reach, int limit) {
        return limited(rows("select " + DEPARTMENT + ", count(o)" + DEPARTMENT_FROM + ORDERS_WHERE + " group by " + DEPARTMENT + " order by count(o) desc", reach), limit);
    }

    private static final String DEPARTMENT = "coalesce(ou.name, su.name, ru.name, 'Unassigned')";
    private static final String DEPARTMENT_FROM = " from Order o left join o.orgUnit ou left join o.submission sub left join sub.submittedBy sb left join sb.orgUnit su"
            + " left join QuickGiftRequest qr on qr.order = o left join qr.requestedBy rb left join rb.orgUnit ru";
    private static final String ORDERS_WHERE = " where o.company.id = :companyId and (:companyWide = true or o.owner.id in :people or o.orgUnit.id in :orgUnits)";

    /** The newest orders by last change, for the fulfilment timeline. */
    List<Order> recentOrders(Reach reach, int limit) {
        return typed("select o" + ORDERS + " order by o.updatedAt desc", Order.class, reach, limit);
    }

    List<Order> ordersWithStatus(Reach reach, Collection<OrderStatus> statuses, int limit) {
        return typed("select o" + ORDERS + " and o.status in :statuses order by o.createdAt desc", Order.class, reach, limit, "statuses", statuses);
    }

    List<Order> ordersFromSource(Reach reach, String source, int limit) {
        return typed("select o" + ORDERS + " and o.source = :source order by o.createdAt desc", Order.class, reach, limit, "source",
                com.wrapzzo.backend.model.OrderSource.valueOf(source));
    }

    List<Order> ordersOfGift(Reach reach, String giftName, int limit) {
        return typed("select o" + ORDERS.replace(" from Order o", " from Order o join o.gift g") + " and g.name = :giftName order by o.createdAt desc",
                Order.class, reach, limit, "giftName", giftName);
    }

    List<Order> ordersOfDepartment(Reach reach, String department, int limit) {
        return typed("select o" + DEPARTMENT_FROM + ORDERS_WHERE + " and " + DEPARTMENT + " = :department order by o.createdAt desc", Order.class, reach, limit,
                "department", department);
    }

    /** Orders that moved on (dispatched, at the warehouse, out for delivery, delivered), newest first -- the feed's order events. */
    List<Order> recentMovedOrders(Reach reach, int limit) {
        return ordersWithStatus(reach, List.of(OrderStatus.DISPATCHED, OrderStatus.RECEIVED_AT_WAREHOUSE, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED), limit)
                .stream().sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt())).toList();
    }

    /** (a doctor's name, phone, orders, PELs, latest order) per doctor, most PELs first. */
    List<Object[]> pelsPerDoctor(Reach reach, boolean deliveredOnly, int limit) {
        String status = deliveredOnly ? " and o.status = 'DELIVERED'" : " and o.status <> 'CANCELLED'";
        Query query = em.createNativeQuery("select (array_agg(o.recipient_name order by o.created_at desc))[1], (array_agg(o.recipient_phone order by o.created_at desc))[1],"
                + " count(*), sum(o.quantity), max(o.created_at)" + ORDERS_SQL + status + HAS_DOCTOR + " group by " + DOCTOR_KEY + " order by sum(o.quantity) desc, max(o.created_at) desc");
        @SuppressWarnings("unchecked")
        List<Object[]> result = bind(query, reach).setMaxResults(limit).getResultList();
        return result;
    }

    // --- Quick Gift requests ---

    Map<String, Long> quickGiftsByStatus(Reach reach) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : rows("select q.status, count(q)" + QUICK_GIFTS + " group by q.status", reach)) {
            counts.put(row[0].toString(), (Long) row[1]);
        }
        return counts;
    }

    long quickGiftsSince(Reach reach, Instant since) {
        return ((Number) bind(em.createQuery("select count(q)" + QUICK_GIFTS + " and q.createdAt >= :since").setParameter("since", since), reach).getSingleResult()).longValue();
    }

    List<QuickGiftRequest> quickGiftsCreatedSince(Reach reach, Instant since, int limit) {
        return typed("select q" + QUICK_GIFTS + " and q.createdAt >= :since order by q.createdAt desc", QuickGiftRequest.class, reach, limit, "since", since);
    }

    List<QuickGiftRequest> quickGiftsWithStatus(Reach reach, QuickGiftStatus status, int limit) {
        return typed("select q" + QUICK_GIFTS + " and q.status = :status order by q.createdAt desc", QuickGiftRequest.class, reach, limit, "status", status);
    }

    List<QuickGiftRequest> quickGiftsBetween(Reach reach, Instant from, Instant to, int limit) {
        return typed("select q" + QUICK_GIFTS + " and q.createdAt >= :from and q.createdAt < :to order by q.createdAt desc", QuickGiftRequest.class, reach, limit,
                "from", from, "to", to);
    }

    List<QuickGiftRequest> newestQuickGifts(Reach reach, int limit) {
        return typed("select q" + QUICK_GIFTS + " order by q.createdAt desc", QuickGiftRequest.class, reach, limit);
    }

    /** Requests someone approved or turned down, newest decision first. */
    List<QuickGiftRequest> newestDecidedQuickGifts(Reach reach, int limit) {
        return typed("select q" + QUICK_GIFTS + " and q.reviewedAt is not null and q.reviewedBy is not null and q.status in :decided order by q.reviewedAt desc",
                QuickGiftRequest.class, reach, limit, "decided", List.of(QuickGiftStatus.APPROVED, QuickGiftStatus.REJECTED));
    }

    // --- campaign submissions ---

    Map<SubmissionStatus, Long> submissionsByStatus(Reach reach) {
        Map<SubmissionStatus, Long> counts = new EnumMap<>(SubmissionStatus.class);
        for (Object[] row : rows("select s.status, count(s)" + SUBMISSIONS + " group by s.status", reach)) {
            counts.put((SubmissionStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    List<Submission> submissionsWithStatus(Reach reach, Collection<SubmissionStatus> statuses, int limit) {
        return typed("select s" + SUBMISSIONS + " and s.status in :statuses order by s.createdAt desc", Submission.class, reach, limit, "statuses", statuses);
    }

    List<Submission> submissionsSubmittedBetween(Reach reach, Instant from, Instant to, int limit) {
        return typed("select s" + SUBMISSIONS + " and s.submittedAt is not null and s.submittedAt >= :from and s.submittedAt < :to order by s.submittedAt desc",
                Submission.class, reach, limit, "from", from, "to", to);
    }

    List<Submission> newestSubmitted(Reach reach, int limit) {
        return typed("select s" + SUBMISSIONS + " and s.submittedAt is not null order by s.submittedAt desc", Submission.class, reach, limit);
    }

    List<Submission> newestDecidedSubmissions(Reach reach, int limit) {
        return typed("select s" + SUBMISSIONS + " and s.reviewedAt is not null and s.reviewedBy is not null and s.status in :decided order by s.reviewedAt desc",
                Submission.class, reach, limit, "decided", List.of(SubmissionStatus.APPROVED, SubmissionStatus.REJECTED));
    }

    // --- the trend: counts per week (Monday, UTC) since a date ---

    Map<LocalDate, Long> quickGiftsPerWeek(Reach reach, Instant since) {
        return perWeek("select date_trunc('week', q.created_at at time zone 'UTC'), count(*)" + QUICK_GIFTS_SQL + " and q.created_at >= :since group by 1", reach, since);
    }

    Map<LocalDate, Long> submissionsPerWeek(Reach reach, Instant since) {
        return perWeek("select date_trunc('week', s.submitted_at at time zone 'UTC'), count(*)" + SUBMISSIONS_SQL
                + " and s.submitted_at is not null and s.submitted_at >= :since group by 1", reach, since);
    }

    private Map<LocalDate, Long> perWeek(String sql, Reach reach, Instant since) {
        Map<LocalDate, Long> weeks = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = bind(em.createNativeQuery(sql).setParameter("since", since), reach).getResultList();
        for (Object[] row : rows) {
            LocalDateTime start = row[0] instanceof java.sql.Timestamp t ? t.toLocalDateTime() : (LocalDateTime) row[0];
            weeks.put(start.toLocalDate(), ((Number) row[1]).longValue());
        }
        return weeks;
    }

    // --- plumbing ---

    private static Instant utc(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    static Instant weekStartInstant(LocalDate monday) {
        return utc(monday);
    }

    private Query bind(Query query, Reach reach) {
        return query.setParameter("companyId", reach.companyId()).setParameter("companyWide", reach.companyWide())
                .setParameter("people", reach.people()).setParameter("orgUnits", reach.orgUnits());
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String jpql, Reach reach) {
        return bind(em.createQuery(jpql), reach).getResultList();
    }

    private static List<Object[]> limited(List<Object[]> rows, int limit) {
        return rows.size() <= limit ? rows : rows.subList(0, limit);
    }

    private <T> List<T> typed(String jpql, Class<T> type, Reach reach, int limit, Object... extraParameters) {
        TypedQuery<T> query = em.createQuery(jpql, type);
        bind(query, reach);
        for (int i = 0; i + 1 < extraParameters.length; i += 2) {
            query.setParameter((String) extraParameters[i], extraParameters[i + 1]);
        }
        return query.setMaxResults(limit).getResultList();
    }
}
