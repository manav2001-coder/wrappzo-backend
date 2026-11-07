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
import com.wrapzzo.backend.model.GiftImage;
import com.wrapzzo.backend.model.OrderStatusHistory;
import com.wrapzzo.backend.service.DashboardQueries.Reach;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.QuickGiftStatus;
import com.wrapzzo.backend.model.Submission;
import com.wrapzzo.backend.model.SubmissionStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CampaignFieldRepository;
import com.wrapzzo.backend.repository.CampaignFormRepository;
import com.wrapzzo.backend.repository.CampaignRepository;
import com.wrapzzo.backend.repository.GiftImageRepository;
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
    private static final int RECENT_ORDER_LIMIT = 8;
    private static final int TREND_WEEKS = 12;
    private static final int DRILLDOWN_LIMIT = 50;
    private static final Set<OrderStatus> IN_PROGRESS_STATUSES = Set.of(OrderStatus.PROCESSING, OrderStatus.PRODUCTION_QUEUE);
    /** Left the vendor, not yet delivered -- a warehouse-routed order's two warehouse stages count as on the way too. */
    private static final Set<OrderStatus> IN_TRANSIT_STATUSES =
            Set.of(OrderStatus.DISPATCHED, OrderStatus.RECEIVED_AT_WAREHOUSE, OrderStatus.OUT_FOR_DELIVERY);

    private final CampaignRepository campaignRepository;
    private final CampaignFormRepository campaignFormRepository;
    private final CampaignFieldRepository campaignFieldRepository;
    private final OrderRepository orderRepository;
    private final QuickGiftRequestRepository quickGiftRequestRepository;
    private final UserRepository userRepository;
    private final CampaignService campaignService;
    private final OrderStatusHistoryService orderStatusHistoryService;
    private final GiftImageRepository giftImageRepository;
    private final FileStorageService fileStorageService;
    private final VisibilityService visibilityService;
    private final DashboardQueries queries;

    /** Who the dashboard is for and which campaigns they see. The records themselves are never loaded: the figures come from {@link DashboardQueries}. */
    private record Scope(Company company, boolean companyWide, Reach reach, List<Campaign> campaigns) {
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary() {
        Scope data = resolveScope();
        Reach reach = data.reach();
        List<Campaign> campaigns = data.campaigns();

        Map<OrderStatus, Long> byStatus = queries.ordersByStatus(reach);
        long campaignsRunning = campaigns.stream().filter(c -> c.getStatus() == CampaignStatus.ACTIVE).count();
        long giftsInProgress = countOf(byStatus, IN_PROGRESS_STATUSES);
        long dispatched = countOf(byStatus, IN_TRANSIT_STATUSES);
        long delivered = byStatus.getOrDefault(OrderStatus.DELIVERED, 0L);

        Map<SubmissionStatus, Long> submissionsByStatus = queries.submissionsByStatus(reach);
        Map<String, Long> submissionStatusBreakdown = new HashMap<>();
        submissionsByStatus.forEach((status, count) -> submissionStatusBreakdown.merge(submissionBucket(status), count, Long::sum));
        long submissionsPending = submissionStatusBreakdown.getOrDefault("PENDING", 0L);

        Map<String, Long> quickGiftStatusBreakdown = queries.quickGiftsByStatus(reach);
        long quickGiftsThisMonth = queries.quickGiftsSince(reach, monthStart());
        long quickGiftsPending = quickGiftStatusBreakdown.getOrDefault(QuickGiftStatus.SUBMITTED.name(), 0L);

        // An Order only exists once approved (automatic for a Campaign submission, manual for a Quick Gift),
        // so every non-cancelled order is a PEL request; delivered ones are served.
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
                queries.pels(reach, false),
                queries.doctors(reach, false),
                queries.pels(reach, true),
                queries.doctors(reach, true),
                queries.ordersBySource(reach),
                quickGiftStatusBreakdown,
                submissionStatusBreakdown,
                activityTrend(reach),
                topGifts(reach),
                topCampaigns(reach, campaigns),
                topDepartments(reach),
                upcomingDeadlines(campaigns),
                recentCampaigns(campaigns),
                recentActivity(reach, campaigns),
                recentOrderTimeline(reach));
    }

    private static long countOf(Map<OrderStatus, Long> byStatus, Set<OrderStatus> statuses) {
        return statuses.stream().mapToLong(status -> byStatus.getOrDefault(status, 0L)).sum();
    }

    /**
     * The records behind one clicked chart datapoint -- same scoping as summary(), so a
     * Member drilling into a slice only ever sees rows that were already counted in their
     * own numbers. Only the newest {@value #DRILLDOWN_LIMIT} are read.
     */
    @Transactional(readOnly = true)
    public DashboardDrilldownResponse drilldown(String dimension, String value) {
        Reach reach = resolveScope().reach();
        return switch (dimension) {
            case "orderSource" -> ordersDrilldown(sourceOrders(reach, value), "Orders — " + titleCase(value));
            case "quickGiftStatus" -> quickGiftsDrilldown(quickGiftsOfStatus(reach, value), "Quick Gift requests — " + titleCase(value));
            case "submissionStatus" -> submissionsDrilldown(
                    queries.submissionsWithStatus(reach, submissionStatusesOf(value), DRILLDOWN_LIMIT), "Campaign submissions — " + titleCase(value));
            case "topGift" -> ordersDrilldown(queries.ordersOfGift(reach, value, DRILLDOWN_LIMIT), "Orders — " + value);
            case "topDepartment" -> ordersDrilldown(queries.ordersOfDepartment(reach, value, DRILLDOWN_LIMIT), "Orders — " + value);
            case "week" -> weekDrilldown(value, reach);
            case "pelsRequested" -> pelsPerDoctorDrilldown(queries.pelsPerDoctor(reach, false, DRILLDOWN_LIMIT), "PELs created per doctor");
            case "pelsServed" -> pelsPerDoctorDrilldown(queries.pelsPerDoctor(reach, true, DRILLDOWN_LIMIT), "PELs served per doctor");
            case "pipelineStage" -> ordersDrilldown(
                    queries.ordersWithStatus(reach, java.util.Arrays.stream(OrderStatus.values()).filter(s -> pipelineStageMatches(s, value)).toList(), DRILLDOWN_LIMIT),
                    "Orders — " + pipelineStageTitle(value));
            case "quickGiftsThisMonth" -> quickGiftsDrilldown(queries.quickGiftsCreatedSince(reach, monthStart(), DRILLDOWN_LIMIT), "Quick Gifts this month");
            default -> new DashboardDrilldownResponse(value, List.of());
        };
    }

    private List<Order> sourceOrders(Reach reach, String source) {
        try {
            return queries.ordersFromSource(reach, source, DRILLDOWN_LIMIT);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private List<QuickGiftRequest> quickGiftsOfStatus(Reach reach, String status) {
        try {
            return queries.quickGiftsWithStatus(reach, QuickGiftStatus.valueOf(status), DRILLDOWN_LIMIT);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /** The submission statuses a chart bucket stands for: "PENDING" is submitted or resubmitted. */
    private List<SubmissionStatus> submissionStatusesOf(String bucket) {
        return java.util.Arrays.stream(SubmissionStatus.values()).filter(status -> submissionBucket(status).equals(bucket)).toList();
    }

    /**
     * Everything the dashboard counts, narrowed to what the viewer can see (see Visibility) --
     * the same rule as the Orders list and the approval queues. Someone who sees only their own
     * records gets a personal dashboard; anyone who sees more gets the team view.
     */
    private Scope resolveScope() {
        User user = currentUser();
        Company company = user.getCompany();
        Visibility visibility = visibilityService.resolve(user);
        return new Scope(company, !visibility.ownRecordsOnly(), Reach.of(company.getId(), visibility), campaignService.visibleCampaigns(user));
    }

    private String submissionBucket(SubmissionStatus status) {
        return (status == SubmissionStatus.SUBMITTED || status == SubmissionStatus.RESUBMITTED) ? "PENDING" : status.name();
    }

    /** Matches PipelineFlow's three stages -- "inProgress" groups PROCESSING+PRODUCTION_QUEUE, same as summary()'s own count. */
    private boolean pipelineStageMatches(OrderStatus status, String stage) {
        return switch (stage) {
            case "inProgress" -> IN_PROGRESS_STATUSES.contains(status);
            case "dispatched" -> IN_TRANSIT_STATUSES.contains(status);
            case "delivered" -> status == OrderStatus.DELIVERED;
            default -> false;
        };
    }

    private String pipelineStageTitle(String stage) {
        return switch (stage) {
            case "inProgress" -> "In progress";
            case "dispatched" -> "Dispatched";
            case "delivered" -> "Delivered";
            default -> stage;
        };
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
                        o.getCampaign() != null ? o.getCampaign().getId() : null,
                        (long) o.getQuantity()))
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
                        null,
                        (long) q.getQuantity()))
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
                        s.getCampaign().getId(),
                        null))
                .toList();
        return new DashboardDrilldownResponse(title, rows);
    }

    /** One row per doctor: how many PELs these orders carry for them, most first. Rows are (name, phone, orders, PELs, latest order). */
    private DashboardDrilldownResponse pelsPerDoctorDrilldown(List<Object[]> doctors, String title) {
        List<Row> rows = doctors.stream()
                .map(doctor -> {
                    String name = doctor[0] != null && !doctor[0].toString().isBlank() ? doctor[0].toString() : (doctor[1] == null ? null : doctor[1].toString());
                    long orders = ((Number) doctor[2]).longValue();
                    long pels = ((Number) doctor[3]).longValue();
                    return new Row(null, name, orders + (orders == 1 ? " order" : " orders"), null, instantOf(doctor[4]), null, pels);
                })
                .sorted(Comparator.comparing(Row::quantity).reversed())
                .limit(DRILLDOWN_LIMIT)
                .toList();
        return new DashboardDrilldownResponse(title, rows);
    }

    private static Instant instantOf(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant();
        }
        return value instanceof java.time.LocalDateTime local ? local.toInstant(ZoneOffset.UTC) : null;
    }

    /** CAMPAIGN orders attribute to the submitter; Quick Gift orders to the requester -- who asked for it, not the recipient. */
    private String submitterName(Order order, Map<UUID, User> quickGiftRequesterByOrderId) {
        if (order.getSource() == OrderSource.CAMPAIGN && order.getSubmission() != null) {
            return order.getSubmission().getSubmittedBy().getName();
        }
        User requester = quickGiftRequesterByOrderId.get(order.getId());
        return requester != null ? requester.getName() : "—";
    }

    /**
     * Most-recently-updated orders first, for the Dashboard's fulfillment timeline. Includes
     * CANCELLED orders (unlike the pipeline-stage counts above) -- the frontend renders those
     * as a distinct terminal state rather than progress along the pipeline, but hiding them
     * here would make an order someone knows exists just silently vanish from the list.
     */
    private List<DashboardResponse.OrderTimelineItem> recentOrderTimeline(Reach reach) {
        List<Order> recent = queries.recentOrders(reach, RECENT_ORDER_LIMIT);
        Map<UUID, User> quickGiftRequesterByOrderId = quickGiftRequestRepository.findByOrder_IdIn(recent.stream().map(Order::getId).toList()).stream()
                .collect(Collectors.toMap(q -> q.getOrder().getId(), QuickGiftRequest::getRequestedBy));
        Map<UUID, List<OrderStatusHistory>> historyByOrderId =
                orderStatusHistoryService.historyByOrderId(recent.stream().map(Order::getId).toList());

        List<UUID> giftIds = recent.stream().map(Order::getGift).filter(g -> g != null).map(g -> g.getId()).distinct().toList();
        Map<UUID, String> firstImageUrlByGiftId = new HashMap<>();
        for (GiftImage image : giftImageRepository.findByGiftIdInOrderByDisplayOrderAsc(giftIds)) {
            firstImageUrlByGiftId.putIfAbsent(image.getGift().getId(), fileStorageService.publicUrl(image.getImageKey()));
        }

        return recent.stream()
                .map(o -> new DashboardResponse.OrderTimelineItem(
                        o.getId(),
                        "ORD-" + o.getOrderNo(),
                        o.getGift() != null ? o.getGift().getName() : "—",
                        o.getGift() != null ? firstImageUrlByGiftId.get(o.getGift().getId()) : null,
                        o.getRecipientName(),
                        submitterName(o, quickGiftRequesterByOrderId),
                        o.getStatus(),
                        o.getSource().name(),
                        o.getCreatedAt(),
                        historyByOrderId.getOrDefault(o.getId(), List.of()).stream()
                                .map(h -> new DashboardResponse.StageEvent(
                                        h.getStatus(), h.getChangedAt(), h.getChangedBy() != null ? h.getChangedBy().getName() : null))
                                .toList()))
                .toList();
    }

    private Instant monthStart() {
        return LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Everything one clicked point on the trend chart represents -- value is
     * "{weekStart}:{seriesKey}" (see TrendPoint.weekStart and the frontend's TrendSeries.key),
     * so a click on the Campaign submissions line shows only that week's submissions, and a
     * click on the Quick Gift requests line shows only that week's requests -- never both,
     * even when both lines have data the same week.
     */
    private DashboardDrilldownResponse weekDrilldown(String rawValue, Reach reach) {
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
        if ("QUICK_GIFT".equals(seriesKey)) {
            return quickGiftsDrilldown(queries.quickGiftsBetween(reach, start, end, DRILLDOWN_LIMIT), "Quick Gift requests — week of " + weekLabel);
        }
        return submissionsDrilldown(queries.submissionsSubmittedBetween(reach, start, end, DRILLDOWN_LIMIT), "Campaign submissions — week of " + weekLabel);
    }

    /** Last TREND_WEEKS calendar weeks (Monday-start), oldest first -- how much is actually happening, week over week. */
    private List<TrendPoint> activityTrend(Reach reach) {
        LocalDate currentWeekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate firstWeek = currentWeekStart.minusWeeks(TREND_WEEKS - 1L);
        Instant since = firstWeek.atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<LocalDate, Long> submissions = queries.submissionsPerWeek(reach, since);
        Map<LocalDate, Long> quickGifts = queries.quickGiftsPerWeek(reach, since);
        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("dd MMM");
        List<TrendPoint> points = new ArrayList<>();
        for (int i = TREND_WEEKS - 1; i >= 0; i--) {
            LocalDate weekStart = currentWeekStart.minusWeeks(i);
            points.add(new TrendPoint(weekStart.format(labelFormat), weekStart.toString(), submissions.getOrDefault(weekStart, 0L), quickGifts.getOrDefault(weekStart, 0L)));
        }
        return points;
    }

    private List<TopGift> topGifts(Reach reach) {
        return queries.topGifts(reach, TOP_N).stream().map(row -> new TopGift((String) row[0], (Long) row[1])).toList();
    }

    private List<TopCampaign> topCampaigns(Reach reach, List<Campaign> campaigns) {
        Map<UUID, String> names = campaigns.stream().collect(Collectors.toMap(Campaign::getId, Campaign::getName, (a, b) -> a));
        return queries.topCampaigns(reach, TOP_N).stream()
                .map(row -> new TopCampaign((UUID) row[0], names.getOrDefault((UUID) row[0], "—"), (Long) row[1]))
                .toList();
    }

    /** CAMPAIGN orders attribute to the submitter's department; Quick Gift orders to the requester's. */
    private List<TopDepartment> topDepartments(Reach reach) {
        return queries.topDepartments(reach, TOP_N).stream().map(row -> new TopDepartment((String) row[0], (Long) row[1])).toList();
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
     * behind or go stale. Each kind contributes only its newest few: the feed shows the newest
     * {@value #RECENT_ACTIVITY_LIMIT} overall, which can come from no deeper than that in any one kind.
     */
    private List<ActivityItem> recentActivity(Reach reach, List<Campaign> campaigns) {
        List<ActivityItem> activity = new ArrayList<>();
        for (Campaign c : campaigns) {
            activity.add(new ActivityItem(c.getCreatedBy().getName(), "created campaign", c.getName(), c.getCreatedAt(), c.getId(), null));
        }
        Map<UUID, Submission> submissions = new java.util.LinkedHashMap<>();
        queries.newestSubmitted(reach, RECENT_ACTIVITY_LIMIT).forEach(s -> submissions.putIfAbsent(s.getId(), s));
        queries.newestDecidedSubmissions(reach, RECENT_ACTIVITY_LIMIT).forEach(s -> submissions.putIfAbsent(s.getId(), s));
        for (Submission s : submissions.values()) {
            if (s.getSubmittedAt() != null) {
                String verb = s.getStatus() == SubmissionStatus.RESUBMITTED ? "resubmitted a recipient for" : "submitted a recipient for";
                activity.add(new ActivityItem(s.getSubmittedBy().getName(), verb, s.getCampaign().getName(), s.getSubmittedAt(), s.getCampaign().getId(), null));
            }
            if (s.getReviewedAt() != null && s.getReviewedBy() != null) {
                String verb = s.getStatus() == SubmissionStatus.APPROVED ? "approved a submission for"
                        : s.getStatus() == SubmissionStatus.REJECTED ? "rejected a submission for"
                        : null;
                if (verb != null) {
                    activity.add(new ActivityItem(s.getReviewedBy().getName(), verb, s.getCampaign().getName(), s.getReviewedAt(), s.getCampaign().getId(), null));
                }
            }
        }
        for (Order o : queries.recentMovedOrders(reach, RECENT_ACTIVITY_LIMIT)) {
            String verb = switch (o.getStatus()) {
                case DISPATCHED -> "dispatched order";
                case RECEIVED_AT_WAREHOUSE -> "received at warehouse order";
                case OUT_FOR_DELIVERY -> "sent out for delivery order";
                case DELIVERED -> "delivered order";
                default -> null;
            };
            if (verb != null) {
                String actor = o.getCreatedBy() != null ? o.getCreatedBy().getName() : "Wrappzo";
                UUID campaignId = o.getCampaign() != null ? o.getCampaign().getId() : null;
                activity.add(new ActivityItem(actor, verb, "ORD-" + o.getOrderNo(), o.getUpdatedAt(), campaignId, null));
            }
        }
        Map<UUID, QuickGiftRequest> quickGifts = new java.util.LinkedHashMap<>();
        queries.newestQuickGifts(reach, RECENT_ACTIVITY_LIMIT).forEach(q -> quickGifts.putIfAbsent(q.getId(), q));
        queries.newestDecidedQuickGifts(reach, RECENT_ACTIVITY_LIMIT).forEach(q -> quickGifts.putIfAbsent(q.getId(), q));
        for (QuickGiftRequest q : quickGifts.values()) {
            activity.add(new ActivityItem(q.getRequestedBy().getName(), "requested Quick Gift", q.getGift().getName(), q.getCreatedAt(), null, q.getId()));
            if (q.getReviewedAt() != null && q.getReviewedBy() != null) {
                String verb = q.getStatus() == QuickGiftStatus.APPROVED ? "approved Quick Gift"
                        : q.getStatus() == QuickGiftStatus.REJECTED ? "rejected Quick Gift"
                        : null;
                if (verb != null) {
                    activity.add(new ActivityItem(q.getReviewedBy().getName(), verb, q.getGift().getName(), q.getReviewedAt(), null, q.getId()));
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
