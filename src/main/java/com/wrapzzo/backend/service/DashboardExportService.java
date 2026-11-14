package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.service.DashboardInsightsService.Window;
import com.wrapzzo.backend.service.DashboardQueries.Reach;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The orders behind the dashboard as a spreadsheet: one row per order in the period, over exactly what the viewer may see. The vendor and
 * price columns are left out unless the viewer may see them, same as everywhere else.
 */
@Service
@RequiredArgsConstructor
public class DashboardExportService {

    static final int MAX_ROWS = 50_000;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final VisibilityService visibilityService;
    private final PricePolicy pricePolicy;
    private final VendorIdentityPolicy vendorIdentityPolicy;

    @PersistenceContext private EntityManager em;

    public record Csv(String fileName, byte[] bytes) {
    }

    @Transactional(readOnly = true)
    public Csv orders(String requestedRange) {
        Window w = DashboardInsightsService.window(requestedRange);
        User user = userRepository.findById(CurrentUser.id()).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + CurrentUser.id()));
        Reach reach = Reach.of(user.getCompany().getId(), visibilityService.resolve(user));
        boolean vendor = vendorIdentityPolicy.canSee();
        boolean price = pricePolicy.canSee();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("select o.order_no, o.created_at, u.name, ou.name, o.recipient_name, o.recipient_pcode, g.name, o.quantity, o.status, o.approval_status,"
                        + " (select max(h.changed_at) from order_status_history h where h.order_id = o.id and h.status = 'DISPATCHED'), v.name, o.total_price"
                        + " from orders o left join users u on u.id = o.owner_id left join org_units ou on ou.id = o.org_unit_id left join gifts g on g.id = o.gift_id"
                        + " left join vendors v on v.id = o.assigned_vendor_id"
                        + " where o.company_id = :companyId and (:companyWide = true or o.owner_id in (:people) or o.org_unit_id in (:orgUnits))"
                        + " and o.created_at >= :from and o.created_at < :to order by o.created_at desc")
                .setParameter("companyId", reach.companyId()).setParameter("companyWide", reach.companyWide())
                .setParameter("people", reach.people()).setParameter("orgUnits", reach.orgUnits())
                .setParameter("from", w.from()).setParameter("to", w.to()).setMaxResults(MAX_ROWS).getResultList();

        StringBuilder csv = new StringBuilder("﻿"); // so Excel reads it as UTF-8
        csv.append(line("Order", "Date", "Requested by", "Area", "Doctor", "Doctor pcode", "PEL", "Quantity", "Status", "Approval", "Dispatched on", "Days to dispatch",
                vendor ? "Vendor" : null, price ? "Total price" : null));
        for (Object[] r : rows) {
            Instant created = instant(r[1]);
            Instant dispatched = instant(r[10]);
            String days = created != null && dispatched != null ? String.valueOf(Math.round(java.time.Duration.between(created, dispatched).toHours() / 2.4) / 10.0) : "";
            csv.append(line("ORD-" + r[0], day(created), text(r[2]), text(r[3]), text(r[4]), text(r[5]), text(r[6]), text(r[7]), text(r[8]), text(r[9]), day(dispatched), days,
                    vendor ? text(r[11]) : null, price ? text(r[12]) : null));
        }
        return new Csv("pel-orders-" + w.range() + ".csv", csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** One CSV line; a null cell means the column isn't in this export at all. */
    private static String line(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (String cell : cells) {
            if (cell == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(escape(cell));
        }
        return sb.append("\r\n").toString();
    }

    /** Quotes a cell and defuses spreadsheet formulas ("=...", "+...", "-...", "@...") that someone typed into a name. */
    static String escape(String value) {
        String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String day(Instant instant) {
        return instant == null ? "" : DAY.format(instant);
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
}
