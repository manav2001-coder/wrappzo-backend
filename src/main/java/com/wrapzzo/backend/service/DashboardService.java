package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.DashboardDrilldownResponse;
import com.wrapzzo.backend.dto.DashboardDrilldownResponse.Row;
import com.wrapzzo.backend.dto.DashboardResponse;
import com.wrapzzo.backend.dto.DashboardResponse.ActivityItem;
import com.wrapzzo.backend.dto.DashboardResponse.RecentCampaign;
import com.wrapzzo.backend.dto.DashboardResponse.TopCampaign;
import com.wrapzzo.backend.dto.DashboardResponse.TopDepartment;
import com.wrapzzo.backend.dto.DashboardResponse.TopGift;
import com.wrapzzo.backend.dto.DashboardResponse.TrendPoint;
import com.wrapzzo.backend.dto.DashboardResponse.UpcomingDeadline;
import com.wrapzzo.backend.model.Campaign;
import com.wrapzzo.backend.model.CampaignStatus;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderSource;
import com.wrapzzo.backend.model.OrderStatus;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftStatus;
import com.wrapzzo.backend.model.Submission;
import com.wrapzzo.backend.model.SubmissionStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CampaignFieldRepository;
import com.wrapzzo.backend.repository.CampaignFormRepository;
import com.wrapzzo.backend.repository.CampaignRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.SubmissionRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Home-screen dashboard (PRD 4.7). A Company Admin (campaign.manage) sees the whole
 * company; a Member sees the same shape of screen, scoped to just the campaigns they're in
 * the audience of and the orders/Quick Gift requests that trace back to them. Everything
 * here is derived from existing tables at read time (no separate audit-log table); a
 * company's data volume is bounded the same way CampaignService's own batched stats assume.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int TOP_N = 5;
    private static final int RECENT_ACTIVITY_LIMIT = 12;
    private static final int TREND_WEEKS = 12;
    private static final int DRILLDOWN_LIMIT = 50;
    private static final Set<OrderStatus> IN_PROGRESS_STATUSES = Set.of(OrderStatus.PROCESSING, OrderStatus.PRODUCTION_QUEUE);

    private final CampaignRepository campaignRepository;
    private final CampaignFormRepository campaignFormRepository;
    private final CampaignFieldRepository campaignFieldRepository;
    private final SubmissionRepository submissionRepository;
    private final OrderRepository orderRepository;
    private final QuickGiftRequestRepository quickGiftRequestRepository;
    private final UserRepository userRepository;
    private final CampaignService campaignService;

    /** Everything the dashboard reads, already scoped to company-wide or just-this-viewer. */
    private record ScopedData(
            Company company,
            boolean companyWide,
            List<Campaign> campaigns,
            List<Order> orders,
            List<QuickGiftRequest> quickGifts,
            List<Submission> submissions,
            Map<UUID, User> quickGiftRequesterByOrderId) {
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary() {
        ScopedData data = resolveScopedData();
        List<Campaign> campaigns = data.campaigns();
        List<Order> orders = data.orders();
        List<QuickGiftRequest> quickGifts = data.quickGifts();
        List<Submission> submissions = data.submissions();

        long campaignsRunning = campaigns.stream().filter(c -> c.getStatus() == CampaignStatus.ACTIVE).count();
        long giftsInProgress = orders.stream().filter(o -> IN_PROGRESS_STATUSES.contains(o.getStatus())).count();
        long dispatched = orders.stream().filter(o -> o.getStatus() == OrderStatus.DISPATCHED).count();
        long delivered = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();

        long submissionsPending = submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.SUBMITTED || s.getStatus() == SubmissionStatus.RESUBMITTED)
                .count();

        Instant monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZoneOffset.UTC).toInstant();
        long quickGiftsThisMonth = quickGifts.stream().filter(q -> !q.getCreatedAt().isBefore(monthStart)).count();
        long quickGiftsPending = quickGifts.stream().filter(q -> q.getStatus() == QuickGiftStatus.SUBMITTED).count();

        Map<String, Long> orderSourceBreakdown = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getSource().name(), Collectors.counting()));
        Map<String, Long> quickGiftStatusBreakdown = quickGifts.stream()
                .collect(Collectors.groupingBy(q -> q.getStatus().name(), Collectors.counting()));
        Map<String, Long> submissionStatusBreakdown = new HashMap<>();
        for (Submission s : submissions) {
            submissionStatusBreakdown.merge(submissionBucket(s), 1L, Long::sum);
        }

        return new DashboardResponse(
                data.company().getName(),
                data.companyWide(),
                campaignsRunning,
                giftsInProgress,
                dispatched,
                delivered,
                submissionsPending,
                quickGiftsThisMonth,
                quickGiftsPending,
                orderSourceBreakdown,
                quickGiftStatusBreakdown,
                submissionStatusBreakdown,
                activityTrend(submissions, quickGifts),
                topGifts(orders),
                topCampaigns(orders, campaigns),
                topDepartments(orders, data.quickGiftRequesterByOrderId()),
                upcomingDeadlines(campaigns),
                recentCampaigns(campaigns),
                recentActivity(campaigns, orders, quickGifts, submissions));
    }

    /**
     * The records behind one clicked chart datapoint -- same scoping as summary(), so a
     * Member drilling into a slice only ever sees rows that were already counted in their
     * own numbers.
     */
    @Transactional(readOnly = true)
    public DashboardDrilldownResponse drilldown(String dimension, String value) {
        ScopedData data = resolveScopedData();
        return switch (dimension) {
            case "orderSource" -> ordersDrilldown(
                    data.orders().stream().filter(o -> o.getSource().name().equals(value)).toList(),
                    "Orders — " + titleCase(value));
            case "quickGiftStatus" -> quickGiftsDrilldown(
                    data.quickGifts().stream().filter(q -> q.getStatus().name().equals(value)).toList(),
                    "Quick Gift requests — " + titleCase(value));
            case "submissionStatus" -> submissionsDrilldown(
                    data.submissions().stream().filter(s -> submissionBucket(s).equals(value)).toList(),
                    "Campaign submissions — " + titleCase(value));
            case "topGift" -> ordersDrilldown(
                    data.orders().stream().filter(o -> o.getGift() != null && o.getGift().getName().equals(value)).toList(),
                    "Orders — " + value);
            case "topDepartment" -> ordersDrilldown(
                    data.orders().stream().filter(o -> departmentOf(o, data.quickGiftRequesterByOrderId()).equals(value)).toList(),
                    "Orders — " + value);
            case "week" -> weekDrilldown(value, data.submissions(), data.quickGifts());
            default -> new DashboardDrilldownResponse(value, List.of());
        };
    }

    private ScopedData resolveScopedData() {
        User user = currentUser();
        Company company = user.getCompany();
        UUID companyId = company.getId();
        boolean companyWide = campaignService.canManage(user);

        List<Campaign> allCampaigns = campaignRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        List<Order> allOrders = orderRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        List<QuickGiftRequest> allQuickGifts = quickGiftRequestRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        List<Submission> allSubmissions = companyWide
                ? submissionRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                : submissionRepository.findBySubmittedByIdOrderByCreatedAtDesc(user.getId());

        // Quick Gift orders no longer carry their requester directly (createdBy becomes the
        // approver on approval, same as a Campaign order) -- resolve requester via this map.
        Map<UUID, User> quickGiftRequesterByOrderId = allQuickGifts.stream()
                .filter(q -> q.getOrder() != null)
                .collect(Collectors.toMap(q -> q.getOrder().getId(), QuickGiftRequest::getRequestedBy));

        List<Campaign> campaigns = companyWide
                ? allCampaigns
                : allCampaigns.stream()
                        .filter(c -> c.getStatus() != CampaignStatus.DRAFT && campaignService.isInAudience(c, user.getId()))
                        .toList();
        List<Order> orders = companyWide
                ? allOrders
                : allOrders.stream().filter(o -> belongsToUser(o, user.getId(), quickGiftRequesterByOrderId)).toList();
        List<QuickGiftRequest> quickGifts = companyWide
                ? allQuickGifts
                : allQuickGifts.stream().filter(q -> q.getRequestedBy().getId().equals(user.getId())).toList();

        return new ScopedData(company, companyWide, campaigns, orders, quickGifts, allSubmissions, quickGiftRequesterByOrderId);
    }

    private String submissionBucket(Submission s) {
        return (s.getStatus() == SubmissionStatus.SUBMITTED || s.getStatus() == SubmissionStatus.RESUBMITTED)
                ? "PENDING"
                : s.getStatus().name();
    }

    /** "QUICK_GIFT" -> "Quick Gift". */
    private static String titleCase(String value) {
        StringBuilder sb = new StringBuilder();
        for (String part : value.toLowerCase().split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private DashboardDrilldownResponse ordersDrilldown(List<Order> orders, String title) {
        List<Row> rows = orders.stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .limit(DRILLDOWN_LIMIT)
                .map(o -> new Row(
                        "ORD-" + o.getOrderNo(),
                        o.getGift() != null ? o.getGift().getName() : "—",
                        o.getRecipientName() != null ? o.getRecipientName() : (o.getCampaign() != null ? o.getCampaign().getName() : "—"),
                        o.getStatus().name(),
                        o.getCreatedAt(),
                        o.getCampaign() != null ? o.getCampaign().getId() : null))
                .toList();
        return new DashboardDrilldownResponse(title, rows);
    }

    private DashboardDrilldownResponse quickGiftsDrilldown(List<QuickGiftRequest> quickGifts, String title) {
        List<Row> rows = quickGifts.stream()
                .sorted(Comparator.comparing(QuickGiftRequest::getCreatedAt).reversed())
                .limit(DRILLDOWN_LIMIT)
                .map(q -> new Row(
                        "QG-" + q.getRequestNo(),
                        q.getGift().getName(),
                        q.getRecipientName(),
                        q.getStatus().name(),
                        q.getCreatedAt(),
                        null))
                .toList();
        return new DashboardDrilldownResponse(title, rows);
    }

    private DashboardDrilldownResponse submissionsDrilldown(List<Submission> submissions, String title) {
        List<Row> rows = submissions.stream()
                .sorted(Comparator.comparing(Submission::getCreatedAt).reversed())
                .limit(DRILLDOWN_LIMIT)
                .map(s -> new Row(
                        null,
                        s.getCampaign().getName(),
                        s.getSubmittedBy().getName(),
                        s.getStatus().name(),
                        s.getSubmittedAt() != null ? s.getSubmittedAt() : s.getCreatedAt(),
                        s.getCampaign().getId()))
                .toList();
        return new DashboardDrilldownResponse(title, rows);
    }

    /**
     * Everything one clicked point on the trend chart represents -- value is
     * "{weekStart}:{seriesKey}" (see TrendPoint.weekStart and the frontend's TrendSeries.key),
     * so a click on the Campaign submissions line shows only that week's submissions, and a
     * click on the Quick Gift requests line shows only that week's requests -- never both,
     * even when both lines have data the same week.
     */
    private DashboardDrilldownResponse weekDrilldown(String rawValue, List<Submission> submissions, List<QuickGiftRequest> quickGifts) {
        String[] parts = rawValue.split(":", 2);
        LocalDate weekStart;
        try {
            weekStart = LocalDate.parse(parts[0]);
        } catch (Exception e) {
            return new DashboardDrilldownResponse("Week", List.of());
        }
        String seriesKey = parts.length > 1 ? parts[1] : null;
        Instant start = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = weekStart.plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        String weekLabel = weekStart.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));

        List<Row> rows = new ArrayList<>();
        String title;
        if ("QUICK_GIFT".equals(seriesKey)) {
            for (QuickGiftRequest q : quickGifts) {
                if (!q.getCreatedAt().isBefore(start) && q.getCreatedAt().isBefore(end)) {
                    rows.add(new Row("QG-" + q.getRequestNo(), q.getGift().getName(), q.getRecipientName(), q.getStatus().name(), q.getCreatedAt(), null));
                }
            }
            title = "Quick Gift requests — week of " + weekLabel;
        } else {
            for (Submission s : submissions) {
                if (s.getSubmittedAt() != null && !s.getSubmittedAt().isBefore(start) && s.getSubmittedAt().isBefore(end)) {
                    rows.add(new Row(null, s.getCampaign().getName(), s.getSubmittedBy().getName(), s.getStatus().name(), s.getSubmittedAt(), s.getCampaign().getId()));
                }
            }
            title = "Campaign submissions — week of " + weekLabel;
        }
        List<Row> sorted = rows.stream().sorted(Comparator.comparing(Row::at).reversed()).limit(DRILLDOWN_LIMIT).toList();
        return new DashboardDrilldownResponse(title, sorted);
    }

    /** Last TREND_WEEKS calendar weeks (Monday-start), oldest first -- how much is actually happening, week over week. */
    private List<TrendPoint> activityTrend(List<Submission> submissions, List<QuickGiftRequest> quickGifts) {
        LocalDate currentWeekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("dd MMM");
        List<TrendPoint> points = new ArrayList<>();
        for (int i = TREND_WEEKS - 1; i >= 0; i--) {
            LocalDate weekStart = currentWeekStart.minusWeeks(i);
            Instant start = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = weekStart.plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            long submissionCount = submissions.stream()
                    .filter(s -> s.getSubmittedAt() != null && !s.getSubmittedAt().isBefore(start) && s.getSubmittedAt().isBefore(end))
                    .count();
            long quickGiftCount = quickGifts.stream()
                    .filter(q -> !q.getCreatedAt().isBefore(start) && q.getCreatedAt().isBefore(end))
                    .count();
            points.add(new TrendPoint(weekStart.format(labelFormat), weekStart.toString(), submissionCount, quickGiftCount));
        }
        return points;
    }

    /** CAMPAIGN orders trace back through their submission's submitter; Quick Gift orders through their requester. */
    private boolean belongsToUser(Order order, UUID userId, Map<UUID, User> quickGiftRequesterByOrderId) {
        if (order.getSource() == OrderSource.CAMPAIGN) {
            return order.getSubmission() != null && order.getSubmission().getSubmittedBy().getId().equals(userId);
        }
        User requester = quickGiftRequesterByOrderId.get(order.getId());
        return requester != null && requester.getId().equals(userId);
    }

    private List<TopGift> topGifts(List<Order> orders) {
        Map<String, Long> counts = orders.stream()
                .filter(o -> o.getGift() != null)
                .collect(Collectors.groupingBy(o -> o.getGift().getName(), Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_N)
                .map(e -> new TopGift(e.getKey(), e.getValue()))
                .toList();
    }

    private List<TopCampaign> topCampaigns(List<Order> orders, List<Campaign> campaigns) {
        Map<UUID, Long> counts = orders.stream()
                .filter(o -> o.getCampaign() != null)
                .collect(Collectors.groupingBy(o -> o.getCampaign().getId(), Collectors.counting()));
        Map<UUID, String> names = campaigns.stream().collect(Collectors.toMap(Campaign::getId, Campaign::getName, (a, b) -> a));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(TOP_N)
                .map(e -> new TopCampaign(e.getKey(), names.getOrDefault(e.getKey(), "—"), e.getValue()))
                .toList();
    }

    private String departmentOf(Order order, Map<UUID, User> quickGiftRequesterByOrderId) {
        OrgUnit unit;
        if (order.getSource() == OrderSource.CAMPAIGN && order.getSubmission() != null) {
            unit = order.getSubmission().getSubmittedBy().getOrgUnit();
        } else {
            User requester = quickGiftRequesterByOrderId.get(order.getId());
            unit = requester != null ? requester.getOrgUnit() : null;
        }
        return unit != null ? unit.getName() : "Unassigned";
    }

    /** CAMPAIGN orders attribute to the submitter's department; Quick Gift orders to the requester's. */
    private List<TopDepartment> topDepartments(List<Order> orders, Map<UUID, User> quickGiftRequesterByOrderId) {
        Map<String, Long> counts = new HashMap<>();
        for (Order order : orders) {
            counts.merge(departmentOf(order, quickGiftRequesterByOrderId), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_N)
                .map(e -> new TopDepartment(e.getKey(), e.getValue()))
                .toList();
    }

    private List<UpcomingDeadline> upcomingDeadlines(List<Campaign> campaigns) {
        Instant now = Instant.now();
        return campaigns.stream()
                .filter(c -> c.getStatus() == CampaignStatus.ACTIVE && c.getSubmissionDeadline() != null && c.getSubmissionDeadline().isAfter(now))
                .sorted(Comparator.comparing(Campaign::getSubmissionDeadline))
                .limit(TOP_N)
                .map(c -> new UpcomingDeadline(c.getId(), "CMP-" + c.getCampaignNo(), c.getName(), c.getSubmissionDeadline()))
                .toList();
    }

    private List<RecentCampaign> recentCampaigns(List<Campaign> campaigns) {
        return campaigns.stream()
                .limit(TOP_N)
                .map(c -> new RecentCampaign(
                        c.getId(),
                        "CMP-" + c.getCampaignNo(),
                        c.getName(),
                        c.getStatus(),
                        fieldCount(c.getId()),
                        c.getSubmissionDeadline(),
                        c.getCreatedBy().getName(),
                        c.getCreatedAt()))
                .toList();
    }

    private int fieldCount(UUID campaignId) {
        return campaignFormRepository.findByCampaignId(campaignId)
                .map(form -> campaignFieldRepository.findByCampaignFormIdOrderByDisplayOrderAsc(form.getId()).size())
                .orElse(0);
    }

    /**
     * Every real operation the team can take that leaves a timestamp: creating a campaign,
     * submitting/resubmitting a recipient, approving/rejecting a submission or Quick Gift
     * request, and an order moving to dispatched/delivered. Rebuilt fresh on every read, so it
     * always reflects whatever just happened -- there's no separate audit-log table to fall
     * behind or go stale.
     */
    private List<ActivityItem> recentActivity(List<Campaign> campaigns, List<Order> orders, List<QuickGiftRequest> quickGifts, List<Submission> submissions) {
        List<ActivityItem> activity = new ArrayList<>();
        for (Campaign c : campaigns) {
            activity.add(new ActivityItem(c.getCreatedBy().getName(), "created campaign", c.getName(), c.getCreatedAt(), c.getId()));
        }
        for (Submission s : submissions) {
            if (s.getSubmittedAt() != null) {
                String verb = s.getStatus() == SubmissionStatus.RESUBMITTED ? "resubmitted a recipient for" : "submitted a recipient for";
                activity.add(new ActivityItem(s.getSubmittedBy().getName(), verb, s.getCampaign().getName(), s.getSubmittedAt(), s.getCampaign().getId()));
            }
            if (s.getReviewedAt() != null && s.getReviewedBy() != null) {
                String verb = s.getStatus() == SubmissionStatus.APPROVED ? "approved a submission for"
                        : s.getStatus() == SubmissionStatus.REJECTED ? "rejected a submission for"
                        : null;
                if (verb != null) {
                    activity.add(new ActivityItem(s.getReviewedBy().getName(), verb, s.getCampaign().getName(), s.getReviewedAt(), s.getCampaign().getId()));
                }
            }
        }
        for (Order o : orders) {
            if (o.getStatus() == OrderStatus.DISPATCHED || o.getStatus() == OrderStatus.DELIVERED) {
                String verb = o.getStatus() == OrderStatus.DISPATCHED ? "dispatched order" : "delivered order";
                String actor = o.getCreatedBy() != null ? o.getCreatedBy().getName() : "Wrapzzo";
                UUID campaignId = o.getCampaign() != null ? o.getCampaign().getId() : null;
                activity.add(new ActivityItem(actor, verb, "ORD-" + o.getOrderNo(), o.getUpdatedAt(), campaignId));
            }
        }
        for (QuickGiftRequest q : quickGifts) {
            activity.add(new ActivityItem(q.getRequestedBy().getName(), "requested Quick Gift", q.getGift().getName(), q.getCreatedAt(), null));
            if (q.getReviewedAt() != null && q.getReviewedBy() != null) {
                String verb = q.getStatus() == QuickGiftStatus.APPROVED ? "approved Quick Gift"
                        : q.getStatus() == QuickGiftStatus.REJECTED ? "rejected Quick Gift"
                        : null;
                if (verb != null) {
                    activity.add(new ActivityItem(q.getReviewedBy().getName(), verb, q.getGift().getName(), q.getReviewedAt(), null));
                }
            }
        }
        return activity.stream()
                .sorted(Comparator.comparing(ActivityItem::at).reversed())
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }
}
