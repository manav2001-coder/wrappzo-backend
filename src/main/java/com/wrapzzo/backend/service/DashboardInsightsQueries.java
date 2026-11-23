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
    /** Days an order may sit in a stage before it is called stuck. */
    static final Map<String, Integer> STUCK_AFTER_DAYS = Map.of(APPROVAL, 2, ARTWORK, 4, PRODUCTION, 7);

    private static final String ORDERS = " from orders o where o.company_id = :companyId and (:companyWide = true or o.owner_id in (:people) or o.org_unit_id in (:orgUnits))";
    private static final String DOCTOR_KEY =
            "coalesce('p:' || nullif(regexp_replace(coalesce(o.recipient_phone, ''), '\\D', '', 'g'), ''), 'n:' || lower(trim(coalesce(o.recipient_name, ''))))";
    private static final String HAS_DOCTOR = "((o.recipient_phone is not null and trim(o.recipient_phone) <> '') or (o.recipient_name is not null and trim(o.recipient_name) <> ''))";
    /** When an order was dispatched: the time of its DISPATCHED history row (an older order without one: when it last changed). */
    private static final String DISPATCHED_AT = "coalesce((select max(h.changed_at) from order_status_history h where h.order_id = o.id and h.status = 'DISPATCHED'), o.updated_at)";

    /** A doctor assigned to a person counts as covered when that person has a live order in the period for them (by phone digits, else name). */
    private static final String COVERED = "exists (select 1 from orders o where o.owner_id = u.id and o.status <> 'CANCELLED' and o.created_at >= :from and o.created_at < :to"
            + " and ((nullif(regexp_replace(coalesce(r.phone, ''), '\\D', '', 'g'), '') is not null"
            + " and regexp_replace(coalesce(o.recipient_phone, ''), '\\D', '', 'g') = regexp_replace(r.phone, '\\D', '', 'g'))"
            + " or lower(trim(coalesce(o.recipient_name, ''))) = lower(trim(r.name))))";

    /**
     * One row per field person in view: anyone with doctors assigned or orders of their own. Set-based on purpose: each person's orders are
     * read once (grouped), and an assigned doctor counts as covered through a hash join on the person's order keys (phone digits, or name).
     * The first version compared every assigned doctor with every order of that person, one pair at a time -- 45 s for a division head.
     */
    private static final String PEOPLE = "with ppl as (select u.id as id, u.name as name, coalesce(ou.name, '') as unit"
            + " from users u left join org_units ou on ou.id = u.org_unit_id"
            + " where u.company_id = :companyId and u.status = 'ACTIVE' and %s"
            + " and (exists (select 1 from recipient_assignments a where a.mr_user_id = u.id) or exists (select 1 from orders o where o.owner_id = u.id))),"
            + " ord as (select o.owner_id as uid,"
            + " coalesce(sum(o.quantity) filter (where o.status <> 'CANCELLED' and o.created_at >= :from and o.created_at < :to), 0) as requested,"
            + " coalesce(sum(o.quantity) filter (where o.status = 'DISPATCHED' and " + DISPATCHED_AT + " >= :from and " + DISPATCHED_AT + " < :to), 0) as dispatched,"
            + " count(*) filter (where o.status not in ('DISPATCHED', 'CANCELLED') and o.approval_status <> 'REJECTED') as in_pipeline,"
            + " max(o.created_at) as last_activity"
            + " from orders o where o.owner_id in (select id from ppl) group by o.owner_id),"
            + " asg as (select a.mr_user_id as uid, count(*) as assigned from recipient_assignments a where a.mr_user_id in (select id from ppl) group by a.mr_user_id),"
            + " keys as (select distinct o.owner_id as uid, nullif(regexp_replace(coalesce(o.recipient_phone, ''), '\\D', '', 'g'), '') as pk, lower(trim(coalesce(o.recipient_name, ''))) as nk"
            + " from orders o where o.owner_id in (select id from ppl) and o.status <> 'CANCELLED' and o.created_at >= :from and o.created_at < :to),"
            + " docs as materialized (select a.mr_user_id as uid, a.recipient_id as rid, nullif(regexp_replace(coalesce(r.phone, ''), '\\D', '', 'g'), '') as pk, lower(trim(r.name)) as nk"
            + " from recipient_assignments a join recipients r on r.id = a.recipient_id where a.mr_user_id in (select id from ppl)),"
            + " hits as (select d.uid, d.rid from docs d join keys k on k.uid = d.uid and k.pk = d.pk"
            + " union select d.uid, d.rid from docs d join keys k on k.uid = d.uid and k.nk = d.nk),"
            + " cov as (select uid, count(*) as covered from hits group by uid)"
            + " select ppl.id, ppl.name, ppl.unit, coalesce(ord.requested, 0) as requested, coalesce(ord.dispatched, 0) as dispatched,"
            + " coalesce(asg.assigned, 0) as assigned, coalesce(cov.covered, 0) as covered, coalesce(ord.in_pipeline, 0) as in_pipeline, ord.last_activity as last_activity"
            + " from ppl left join ord on ord.uid = ppl.id left join asg on asg.uid = ppl.id left join cov on cov.uid = ppl.id"
            + " order by requested asc, ppl.name asc";

    private static final String STAGE_CASE = "case when o.approval_status = 'PENDING' then 'APPROVAL'"
                + " when o.status in ('PROCESSING', 'PRODUCTION_QUEUE') and not exists (select 1 from order_designs d where d.order_id = o.id and d.status = 'APPROVED') then 'ARTWORK'"
                + " else 'PRODUCTION' end";
    private static final String AGE_DAYS = "extract(epoch from (now() - coalesce((select max(h.changed_at) from order_status_history h where h.order_id = o.id and h.status = o.status), o.created_at))) / 86400.0";

    @PersistenceContext private EntityManager em;

    Period period(Reach reach, Instant from, Instant to) {
        Object[] made = (Object[]) bind(em.createNativeQuery("select coalesce(sum(o.quantity), 0), count(distinct " + DOCTOR_KEY + ") filter (where " + HAS_DOCTOR + ")"
                + ORDERS + " and o.status <> 'CANCELLED' and o.created_at >= :from and o.created_at < :to"), reach, from, to).getSingleResult();
        Object[] dispatched = (Object[]) bind(em.createNativeQuery("select coalesce(sum(o.quantity), 0), count(distinct " + DOCTOR_KEY + ") filter (where " + HAS_DOCTOR + "),"
                + " avg(extract(epoch from (" + DISPATCHED_AT + " - o.created_at)) / 86400.0)"
                + ORDERS + " and o.status = 'DISPATCHED' and " + DISPATCHED_AT + " >= :from and " + DISPATCHED_AT + " < :to"), reach, from, to).getSingleResult();
        Double avg = dispatched[2] == null ? null : Math.round(((Number) dispatched[2]).doubleValue() * 10.0) / 10.0;
        return new Period(num(made[0]), num(dispatched[0]), num(made[1]), num(dispatched[1]), avg);
    }

    /** Orders not yet dispatched, bucketed into the three stages, each with how many have waited too long and the longest wait. */
    List<StageAging> stages(Reach reach) {
        Map<String, double[]> byStage = new LinkedHashMap<>();
        for (String stage : List.of(APPROVAL, ARTWORK, PRODUCTION)) {
            byStage.put(stage, new double[] {0, 0, 0});
        }
        @SuppressWarnings("unchecked")
        List<Object[]> orders = bind(em.createNativeQuery("select " + STAGE_CASE + ", " + AGE_DAYS + ORDERS
                + " and o.status not in ('DISPATCHED', 'CANCELLED') and o.approval_status <> 'REJECTED'"), reach, null, null).getResultList();
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
                + " and o.status not in ('DISPATCHED', 'CANCELLED') and o.approval_status <> 'REJECTED') o where o.stage = :stage order by o.age desc")
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

    /** PELs asked for and PELs dispatched per week (Monday, UTC), for the last weeks, oldest first, empty weeks included. */
    List<WeekPoint> weekly(Reach reach, LocalDate firstMonday, int weeks) {
        Instant since = firstMonday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<LocalDate, Long> requested = perWeek("select date_trunc('week', o.created_at at time zone 'UTC'), sum(o.quantity)" + ORDERS
                + " and o.status <> 'CANCELLED' and o.created_at >= :from group by 1", reach, since);
        Map<LocalDate, Long> dispatchedPerWeek = perWeek("select date_trunc('week', " + DISPATCHED_AT + " at time zone 'UTC'), sum(o.quantity)" + ORDERS
                + " and o.status = 'DISPATCHED' and " + DISPATCHED_AT + " >= :from group by 1", reach, since);
        DateTimeFormatter label = DateTimeFormatter.ofPattern("d MMM");
        List<WeekPoint> points = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            LocalDate monday = firstMonday.plusWeeks(i);
            points.add(new WeekPoint(monday.format(label), monday.toString(), requested.getOrDefault(monday, 0L), dispatchedPerWeek.getOrDefault(monday, 0L)));
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

    /** The people in view, fewest PELs first -- the ones who need a nudge sit at the top. One query for all of them; coverage, size and the table are read from it. */
    List<TeamMember> people(Reach reach, UUID viewerId, boolean viewerOnly, Instant from, Instant to) {
        String who = viewerOnly ? "u.id = :viewerId" : "u.id <> :viewerId and (:companyWide = true or u.id in (:people) or u.org_unit_id in (:orgUnits))";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = bind(em.createNativeQuery(PEOPLE.formatted(who)), reach, from, to).setParameter("viewerId", viewerId).getResultList();
        List<TeamMember> members = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            members.add(new TeamMember((UUID) r[0], (String) r[1], (String) r[2], num(r[3]), num(r[4]), num(r[5]), num(r[6]), num(r[7]), instant(r[8])));
        }
        return members;
    }

    /** Doctors assigned across the given people, and how many of them have a live order in the period. */
    static Coverage coverageOf(List<TeamMember> people) {
        long assigned = 0;
        long covered = 0;
        for (TeamMember m : people) {
            assigned += m.doctorsAssigned();
            covered += m.doctorsCovered();
        }
        return new Coverage(assigned, covered);
    }

    /** Doctors assigned to one person with no live PEL order in the period: (name, speciality / clinic, assigned at), longest-waiting first. */
    List<Object[]> uncoveredDoctors(UUID userId, Instant from, Instant to, int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("select r.name, concat_ws(' · ', nullif(r.speciality, ''), nullif(r.clinic_name, '')), a.assigned_at, r.pcode"
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
        String open = "(o.status not in ('DISPATCHED', 'CANCELLED') and o.approval_status <> 'REJECTED')";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = bind(em.createNativeQuery("select v.id, v.name, count(*),"
                + " count(*) filter (where o.status = 'DISPATCHED'),"
                + " count(*) filter (where " + open + "),"
                + " count(*) filter (where " + open + " and o.created_at < now() - interval '10 days'),"
                + " avg(extract(epoch from (disp.at - o.created_at)) / 86400.0) filter (where disp.at is not null),"
                + " coalesce(sum(dz.total), 0), coalesce(sum(dz.rejected), 0)"
                + " from orders o join vendors v on v.id = o.assigned_vendor_id"
                + " left join lateral (select min(h.changed_at) as at from order_status_history h where h.order_id = o.id and h.status = 'DISPATCHED') disp on true"
                + " left join lateral (select count(*) as total, count(*) filter (where d.status = 'REJECTED') as rejected from order_designs d where d.order_id = o.id) dz on true"
                + " where o.company_id = :companyId and (:companyWide = true or o.owner_id in (:people) or o.org_unit_id in (:orgUnits))"
                + " and o.status <> 'CANCELLED' and o.created_at >= :from and o.created_at < :to"
                + " group by v.id, v.name order by count(*) desc"), reach, from, to).setMaxResults(20).getResultList();
        List<VendorScore> scores = new ArrayList<>();
        for (Object[] r : rows) {
            scores.add(new VendorScore((UUID) r[0], (String) r[1], num(r[2]), num(r[3]), num(r[4]), num(r[5]), oneDecimal(r[6]), num(r[7]), num(r[8])));
        }
        return scores;
    }

    private static Double oneDecimal(Object value) {
        return value == null ? null : Math.round(((Number) value).doubleValue() * 10.0) / 10.0;
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
