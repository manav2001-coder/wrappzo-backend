package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.DashboardInsightsResponse.Coverage;
import com.wrapzzo.backend.dto.DashboardInsightsResponse.Period;
import com.wrapzzo.backend.dto.DashboardInsightsResponse.Spend;
import com.wrapzzo.backend.dto.DashboardInsightsResponse.SpendLine;
import com.wrapzzo.backend.dto.DashboardInsightsResponse.StageAging;
import com.wrapzzo.backend.dto.DashboardInsightsResponse.VendorScore;
import com.wrapzzo.backend.dto.DashboardInsightsResponse.TeamMember;
import com.wrapzzo.backend.dto.DashboardInsightsResponse.WeekPoint;
import com.wrapzzo.backend.service.DashboardQueries.Reach;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Period figures, order aging, doctor coverage and per-person rows for the role-shaped dashboard. Same reach rule as
 * {@link DashboardQueries}: only orders the viewer may see are counted, summed in the database.
 */
@Component
class DashboardInsightsQueries {

    static final String APPROVAL = "APPROVAL";
    static final String ARTWORK = "ARTWORK";
    static final String PRODUCTION = "PRODUCTION";
    static final String TRANSIT = "TRANSIT";
    /** Days an order may sit in a stage before it is called stuck. */
    static final Map<String, Integer> STUCK_AFTER_DAYS = Map.of(APPROVAL, 2, ARTWORK, 4, PRODUCTION, 7, TRANSIT, 5);

    private static final String ORDERS = " from orders o where o.company_id = :companyId and (:companyWide = true or o.owner_id in (:people) or o.org_unit_id in (:orgUnits))";
    private static final String DOCTOR_KEY =
            "coalesce('p:' || nullif(regexp_replace(coalesce(o.recipient_phone, ''), '\\D', '', 'g'), ''), 'n:' || lower(trim(coalesce(o.recipient_name, ''))))";
    private static final String HAS_DOCTOR = "((o.recipient_phone is not null and trim(o.recipient_phone) <> '') or (o.recipient_name is not null and trim(o.recipient_name) <> ''))";
    /** When an order was delivered: the time of its DELIVERED history row (an older order without one: when it last changed). */
    private static final String DELIVERED_AT = "coalesce((select max(h.changed_at) from order_status_history h where h.order_id = o.id and h.status = 'DELIVERED'), o.updated_at)";

    /** A doctor assigned to a person counts as covered when that person has a live order in the period for them (by phone digits, else name). */
    private static final String COVERED = "exists (select 1 from orders o where o.owner_id = u.id and o.status <> 'CANCELLED' and o.created_at >= :from and o.created_at < :to"
            + " and ((nullif(regexp_replace(coalesce(r.phone, ''), '\\D', '', 'g'), '') is not null"
            + " and regexp_replace(coalesce(o.recipient_phone, ''), '\\D', '', 'g') = regexp_replace(r.phone, '\\D', '', 'g'))"
            + " or lower(trim(coalesce(o.recipient_name, ''))) = lower(trim(r.name))))";

    /** One row per field person in view: anyone with doctors assigned or orders of their own. */
    private static final String PEOPLE = "select u.id as id, u.name as name, coalesce(ou.name, '') as unit,"
            + " (select coalesce(sum(o.quantity), 0) from orders o where o.owner_id = u.id and o.status <> 'CANCELLED' and o.created_at >= :from and o.created_at < :to) as requested,"
            + " (select coalesce(sum(o.quantity), 0) from orders o where o.owner_id = u.id and o.status = 'DELIVERED' and " + DELIVERED_AT + " >= :from and " + DELIVERED_AT + " < :to) as served,"
            + " (select count(*) from recipient_assignments a where a.mr_user_id = u.id) as assigned,"
            + " (select count(*) from recipient_assignments a join recipients r on r.id = a.recipient_id where a.mr_user_id = u.id and " + COVERED + ") as covered,"
            + " (select count(*) from orders o where o.owner_id = u.id and o.status not in ('DELIVERED', 'CANCELLED') and o.approval_status <> 'REJECTED') as in_pipeline,"
            + " (select max(o.created_at) from orders o where o.owner_id = u.id) as last_activity"
            + " from users u left join org_units ou on ou.id = u.org_unit_id"
            + " where u.company_id = :companyId and u.status = 'ACTIVE' and %s"
            + " and (exists (select 1 from recipient_assignments a where a.mr_user_id = u.id) or exists (select 1 from orders o where o.owner_id = u.id))";

    private static final String STAGE_CASE = "case when o.approval_status = 'PENDING' then 'APPROVAL'"
                + " when o.status in ('PROCESSING', 'PRODUCTION_QUEUE') and not exists (select 1 from order_designs d where d.order_id = o.id and d.status = 'APPROVED') then 'ARTWORK'"
                + " when o.status in ('PROCESSING', 'PRODUCTION_QUEUE') then 'PRODUCTION' else 'TRANSIT' end";
    private static final String AGE_DAYS = "extract(epoch from (now() - coalesce((select max(h.changed_at) from order_status_history h where h.order_id = o.id and h.status = o.status), o.created_at))) / 86400.0";

    @PersistenceContext private EntityManager em;

    Period period(Reach reach, Instant from, Instant to) {
        Object[] made = (Object[]) bind(em.createNativeQuery("select coalesce(sum(o.quantity), 0), count(distinct " + DOCTOR_KEY + ") filter (where " + HAS_DOCTOR + ")"
                + ORDERS + " and o.status <> 'CANCELLED' and o.created_at >= :from and o.created_at < :to"), reach, from, to).getSingleResult();
        Object[] delivered = (Object[]) bind(em.createNativeQuery("select coalesce(sum(o.quantity), 0), count(distinct " + DOCTOR_KEY + ") filter (where " + HAS_DOCTOR + "),"
                + " avg(extract(epoch from (" + DELIVERED_AT + " - o.created_at)) / 86400.0)"
                + ORDERS + " and o.status = 'DELIVERED' and " + DELIVERED_AT + " >= :from and " + DELIVERED_AT + " < :to"), reach, from, to).getSingleResult();
        Double avg = delivered[2] == null ? null : Math.round(((Number) delivered[2]).doubleValue() * 10.0) / 10.0;
        return new Period(num(made[0]), num(delivered[0]), num(made[1]), num(delivered[1]), avg);
    }

    /** Orders still on their way, bucketed into the four stages, each with how many have waited too long and the longest wait. */
    List<StageAging> stages(Reach reach) {
        Map<String, double[]> byStage = new LinkedHashMap<>();
        for (String stage : List.of(APPROVAL, ARTWORK, PRODUCTION, TRANSIT)) {
            byStage.put(stage, new double[] {0, 0, 0});
        }
        @SuppressWarnings("unchecked")
        List<Object[]> orders = bind(em.createNativeQuery("select " + STAGE_CASE + ", " + AGE_DAYS + ORDERS
                + " and o.status not in ('DELIVERED', 'CANCELLED') and o.approval_status <> 'REJECTED'"), reach, null, null).getResultList();
        for (Object[] row : orders) {
            tally(byStage.get((String) row[0]), ((Number) row[1]).doubleValue(), STUCK_AFTER_DAYS.get((String) row[0]));
        }
        // A request nobody has approved yet has no order, but it is waiting on an approver all the same.
        @SuppressWarnings("unchecked")
        List<Object[]> requests = bind(em.createNativeQuery("select extract(epoch from (now() - q.created_at)) / 86400.0"
                + " from quick_gift_requests q where q.company_id = :companyId and q.status = 'SUBMITTED'"
                + " and (:companyWide = true or q.requested_by in (:people) or q.org_unit_id in (:orgUnits))"), reach, null, null).getResultList();
        for (Object age1 : requests) {
            tally(byStage.get(APPROVAL), ((Number) age1).doubleValue(), STUCK_AFTER_DAYS.get(APPROVAL));
        }
        List<StageAging> stages = new ArrayList<>();
        byStage.forEach((stage, t) -> stages.add(new StageAging(stage, (long) t[0], (long) t[1], (long) Math.floor(t[2]), STUCK_AFTER_DAYS.get(stage))));
        return stages;
    }

    /** The orders in one stage, longest-waiting first. */
    List<UUID> orderIdsInStage(Reach reach, String stage, int limit) {
        @SuppressWarnings("unchecked")
        List<Object> ids = bind(em.createNativeQuery("select o.id from (select o.id as id, " + STAGE_CASE + " as stage, " + AGE_DAYS + " as age" + ORDERS
                + " and o.status not in ('DELIVERED', 'CANCELLED') and o.approval_status <> 'REJECTED') o where o.stage = :stage order by o.age desc")
                .setParameter("stage", stage), reach, null, null).setMaxResults(limit).getResultList();
        return ids.stream().map(id -> (UUID) id).toList();
    }

    private static void tally(double[] totals, double ageDays, int threshold) {
        totals[0]++;
        if (ageDays > threshold) {
            totals[1]++;
        }
        totals[2] = Math.max(totals[2], ageDays);
    }

    long rejectedRequests(Reach reach, Instant from, Instant to) {
        return num(bind(em.createNativeQuery("select count(*) from quick_gift_requests q where q.company_id = :companyId and q.status = 'REJECTED'"
                + " and q.created_at >= :from and q.created_at < :to and (:companyWide = true or q.requested_by in (:people) or q.org_unit_id in (:orgUnits))"), reach, from, to)
                .getSingleResult());
    }

    /** PELs asked for and PELs delivered per week (Monday, UTC), for the last weeks, oldest first, empty weeks included. */
    List<WeekPoint> weekly(Reach reach, LocalDate firstMonday, int weeks) {
        Instant since = firstMonday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<LocalDate, Long> requested = perWeek("select date_trunc('week', o.created_at at time zone 'UTC'), sum(o.quantity)" + ORDERS
                + " and o.status <> 'CANCELLED' and o.created_at >= :from group by 1", reach, since);
        Map<LocalDate, Long> delivered = perWeek("select date_trunc('week', " + DELIVERED_AT + " at time zone 'UTC'), sum(o.quantity)" + ORDERS
                + " and o.status = 'DELIVERED' and " + DELIVERED_AT + " >= :from group by 1", reach, since);
        DateTimeFormatter label = DateTimeFormatter.ofPattern("d MMM");
        List<WeekPoint> points = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            LocalDate monday = firstMonday.plusWeeks(i);
            points.add(new WeekPoint(monday.format(label), monday.toString(), requested.getOrDefault(monday, 0L), delivered.getOrDefault(monday, 0L)));
        }
        return points;
    }

    private Map<LocalDate, Long> perWeek(String sql, Reach reach, Instant since) {
        Map<LocalDate, Long> weeks = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = bind(em.createNativeQuery(sql), reach, since, null).getResultList();
        for (Object[] row : rows) {
            LocalDateTime start = row[0] instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) row[0];
            weeks.put(start.toLocalDate(), num(row[1]));
        }
        return weeks;
    }

    /** viewerOnly = just the viewer (a personal dashboard); otherwise the people in view other than the viewer. */
    Coverage coverage(Reach reach, UUID viewerId, boolean viewerOnly, Instant from, Instant to) {
        Object[] row = (Object[]) people("select coalesce(sum(assigned), 0), coalesce(sum(covered), 0) from (%s) t", reach, viewerId, viewerOnly, from, to, 0).getSingleResult();
        return new Coverage(num(row[0]), num(row[1]));
    }

    long teamSize(Reach reach, UUID viewerId, boolean viewerOnly, Instant from, Instant to) {
        return num(people("select count(*) from (%s) t", reach, viewerId, viewerOnly, from, to, 0).getSingleResult());
    }

    /** The people in view, fewest PELs first -- the ones who need a nudge sit at the top. */
    List<TeamMember> team(Reach reach, UUID viewerId, boolean viewerOnly, Instant from, Instant to, int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = people("select * from (%s) t order by requested asc, name asc", reach, viewerId, viewerOnly, from, to, limit).getResultList();
        List<TeamMember> members = new ArrayList<>();
        for (Object[] r : rows) {
            members.add(new TeamMember((UUID) r[0], (String) r[1], (String) r[2], num(r[3]), num(r[4]), num(r[5]), num(r[6]), num(r[7]), instant(r[8])));
        }
        return members;
    }

    /** Doctors assigned to one person with no live PEL order in the period: (name, speciality / clinic, assigned at), longest-waiting first. */
    List<Object[]> uncoveredDoctors(UUID userId, Instant from, Instant to, int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("select r.name, concat_ws(' · ', nullif(r.speciality, ''), nullif(r.clinic_name, '')), a.assigned_at"
                        + " from recipient_assignments a join recipients r on r.id = a.recipient_id join users u on u.id = a.mr_user_id"
                        + " where a.mr_user_id = :userId and not " + COVERED + " order by a.assigned_at asc")
                .setParameter("userId", userId).setParameter("from", from).setParameter("to", to).setMaxResults(limit).getResultList();
        return rows;
    }

    /** What the PELs ordered in the period cost: the total, by PEL and by the unit the order came from. Only call for someone who may see prices. */
    Spend spend(Reach reach, Instant from, Instant to, Instant previousFrom) {
        BigDecimal total = money(bind(em.createNativeQuery("select coalesce(sum(o.total_price), 0)" + ORDERS + LIVE_IN_PERIOD), reach, from, to).getSingleResult());
        BigDecimal previous = previousFrom == null ? null
                : money(bind(em.createNativeQuery("select coalesce(sum(o.total_price), 0)" + ORDERS + LIVE_IN_PERIOD), reach, previousFrom, from).getSingleResult());
        long pels = num(bind(em.createNativeQuery("select coalesce(sum(o.quantity), 0)" + ORDERS + LIVE_IN_PERIOD), reach, from, to).getSingleResult());
        BigDecimal perPel = pels == 0 ? null : total.divide(BigDecimal.valueOf(pels), 2, java.math.RoundingMode.HALF_UP);
        return new Spend(total, previous, perPel,
                lines("select g.name, sum(o.total_price)" + ORDERS.replace("from orders o", "from orders o join gifts g on g.id = o.gift_id") + LIVE_IN_PERIOD
                        + " group by g.name having sum(o.total_price) > 0 order by sum(o.total_price) desc, 1", reach, from, to),
                lines("select coalesce(ou.name, 'Unassigned'), sum(o.total_price)" + ORDERS.replace("from orders o", "from orders o left join org_units ou on ou.id = o.org_unit_id") + LIVE_IN_PERIOD
                        + " group by 1 having sum(o.total_price) > 0 order by sum(o.total_price) desc, 1", reach, from, to));
    }

    private static final String LIVE_IN_PERIOD = " and o.status <> 'CANCELLED' and o.created_at >= :from and o.created_at < :to";

    private List<SpendLine> lines(String sql, Reach reach, Instant from, Instant to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = bind(em.createNativeQuery(sql), reach, from, to).setMaxResults(5).getResultList();
        return rows.stream().map(r -> new SpendLine((String) r[0], money(r[1]))).toList();
    }

    private static BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Per vendor, for the orders placed in the period -- fewest problems last. Only call for someone who may see vendor identity. */
    List<VendorScore> vendors(Reach reach, Instant from, Instant to) {
        String open = "(o.status not in ('DELIVERED', 'CANCELLED') and o.approval_status <> 'REJECTED')";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = bind(em.createNativeQuery("select v.id, v.name, count(*),"
                + " count(*) filter (where o.status = 'DELIVERED'),"
                + " count(*) filter (where " + open + "),"
                + " count(*) filter (where " + open + " and o.created_at < now() - interval '10 days'),"
                + " avg(extract(epoch from (disp.at - o.created_at)) / 86400.0) filter (where disp.at is not null),"
                + " avg(extract(epoch from (" + DELIVERED_AT + " - o.created_at)) / 86400.0) filter (where o.status = 'DELIVERED'),"
                + " coalesce(sum(dz.total), 0), coalesce(sum(dz.rejected), 0)"
                + " from orders o join vendors v on v.id = o.assigned_vendor_id"
                + " left join lateral (select min(h.changed_at) as at from order_status_history h where h.order_id = o.id and h.status = 'DISPATCHED') disp on true"
                + " left join lateral (select count(*) as total, count(*) filter (where d.status = 'REJECTED') as rejected from order_designs d where d.order_id = o.id) dz on true"
                + " where o.company_id = :companyId and (:companyWide = true or o.owner_id in (:people) or o.org_unit_id in (:orgUnits))"
                + " and o.status <> 'CANCELLED' and o.created_at >= :from and o.created_at < :to"
                + " group by v.id, v.name order by count(*) desc"), reach, from, to).setMaxResults(20).getResultList();
        List<VendorScore> scores = new ArrayList<>();
        for (Object[] r : rows) {
            scores.add(new VendorScore((UUID) r[0], (String) r[1], num(r[2]), num(r[3]), num(r[4]), num(r[5]), oneDecimal(r[6]), oneDecimal(r[7]), num(r[8]), num(r[9])));
        }
        return scores;
    }

    private static Double oneDecimal(Object value) {
        return value == null ? null : Math.round(((Number) value).doubleValue() * 10.0) / 10.0;
    }

    private Query people(String wrapper, Reach reach, UUID viewerId, boolean viewerOnly, Instant from, Instant to, int limit) {
        String who = viewerOnly ? "u.id = :viewerId" : "u.id <> :viewerId and (:companyWide = true or u.id in (:people) or u.org_unit_id in (:orgUnits))";
        Query query = bind(em.createNativeQuery(wrapper.formatted(PEOPLE.formatted(who))), reach, from, to).setParameter("viewerId", viewerId);
        if (limit > 0) {
            query.setMaxResults(limit);
        }
        return query;
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof Timestamp t) {
            return t.toInstant();
        }
        return value instanceof OffsetDateTime o ? o.toInstant() : null;
    }

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    /** Sets whichever of the shared parameters the query actually uses. */
    private Query bind(Query query, Reach reach, Instant from, Instant to) {
        set(query, "companyId", reach.companyId());
        set(query, "companyWide", reach.companyWide());
        set(query, "people", reach.people());
        set(query, "orgUnits", reach.orgUnits());
        set(query, "from", from);
        set(query, "to", to);
        return query;
    }

    private static void set(Query query, String name, Object value) {
        if (value != null && query.getParameters().stream().anyMatch(p -> name.equals(p.getName()))) {
            query.setParameter(name, value);
        }
    }
}
