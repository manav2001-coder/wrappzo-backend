package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Emails whoever an artwork proof waits on: straight away when it lands on them, then a daily reminder while it
 * sits (at most {@link #MAX_REMINDERS} of them, a day apart, so an absent approver is not mailed forever).
 * A person with several proofs waiting gets one email listing them all. The emails name the order, the gift and
 * the version -- never the vendor, whose identity the ordering side does not see.
 */
@Service
@Slf4j
public class ArtworkApprovalReminderService {

    static final int MAX_REMINDERS = 3;
    static final Duration REMINDER_EVERY = Duration.ofHours(24);

    private final OrderDesignRepository designs;
    private final OrderDesignService orderDesignService;
    private final EmailSender emailSender;
    private final String frontendBaseUrl;

    public ArtworkApprovalReminderService(OrderDesignRepository designs, OrderDesignService orderDesignService, EmailSender emailSender,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.designs = designs;
        this.orderDesignService = orderDesignService;
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** A proof just started waiting: tell the people it waits on. */
    @Transactional(readOnly = true)
    public void notifyAwaiting(UUID designId) {
        OrderDesign design = designs.findById(designId).orElse(null);
        if (design == null) {
            return;
        }
        for (User approver : orderDesignService.awaitedApprovers(design)) {
            emailSender.send(approver.getEmail(), "Artwork proof " + label(design) + " is waiting for your approval",
                    body(approver, List.of(design), false));
        }
    }

    /** The daily job: remind each approver of the proofs that have waited a day (or another day) since the last email. */
    @Transactional
    public String sendDueReminders() {
        Instant now = Instant.now();
        List<OrderDesign> waiting = designs.findByStatusInAndRemindersSentLessThanOrderByAwaitingSinceAsc(
                List.of(OrderDesignStatus.PENDING_REVIEW, OrderDesignStatus.PENDING_FINAL_APPROVAL), MAX_REMINDERS);
        Map<UUID, List<OrderDesign>> byApprover = new LinkedHashMap<>();
        Map<UUID, User> approvers = new LinkedHashMap<>();
        for (OrderDesign design : waiting) {
            Instant since = design.getLastReminderAt() != null ? design.getLastReminderAt() : design.getAwaitingSince();
            if (since == null || since.plus(REMINDER_EVERY).isAfter(now)) {
                continue;
            }
            for (User approver : orderDesignService.awaitedApprovers(design)) {
                approvers.putIfAbsent(approver.getId(), approver);
                byApprover.computeIfAbsent(approver.getId(), id -> new ArrayList<>()).add(design);
            }
        }
        int emails = 0;
        int failed = 0;
        Map<UUID, OrderDesign> reminded = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<OrderDesign>> entry : byApprover.entrySet()) {
            User approver = approvers.get(entry.getKey());
            List<OrderDesign> items = entry.getValue();
            try {
                String subject = items.size() == 1
                        ? "Reminder: artwork proof " + label(items.get(0)) + " is waiting for your approval"
                        : "Reminder: " + items.size() + " artwork proofs are waiting for your approval";
                emailSender.send(approver.getEmail(), subject, body(approver, items, true));
                emails++;
                items.forEach(design -> reminded.put(design.getId(), design));
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not send the artwork reminder to {}: {}", approver.getEmail(), e.getMessage());
            }
        }
        reminded.values().forEach(design -> {
            design.setRemindersSent(design.getRemindersSent() + 1);
            design.setLastReminderAt(now);
        });
        return emails + " reminder email(s) for " + reminded.size() + " proof(s)" + (failed > 0 ? ", " + failed + " failed" : "");
    }

    private static String label(OrderDesign design) {
        return "v" + design.getVersionNo() + " for ORD-" + design.getOrder().getOrderNo();
    }

    private String body(User approver, List<OrderDesign> items, boolean reminder) {
        StringBuilder rows = new StringBuilder();
        for (OrderDesign design : items) {
            String gift = design.getOrder().getGift() != null ? design.getOrder().getGift().getName() : "Gift";
            rows.append("<li style=\"margin:6px 0;\"><strong>ORD-%d</strong> &middot; %s &middot; for %s &middot; proof v%d</li>".formatted(
                    design.getOrder().getOrderNo(), HtmlUtils.htmlEscape(gift),
                    HtmlUtils.htmlEscape(String.valueOf(design.getOrder().getRecipientName())), design.getVersionNo()));
        }
        String intro = items.size() == 1
                ? (reminder ? "An artwork proof is still waiting for your approval:" : "An artwork proof is waiting for your approval:")
                : items.size() + " artwork proofs are still waiting for your approval:";
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;">
                  <p style="font-size:15px;color:#111827;">Hi %s,</p>
                  <p style="font-size:15px;color:#111827;line-height:1.6;">%s</p>
                  <ul style="font-size:14px;color:#111827;line-height:1.5;padding-left:18px;">%s</ul>
                  <p style="text-align:center;margin:28px 0;">
                    <a href="%s" style="display:inline-block;padding:12px 28px;background:#4f46e5;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600;font-size:15px;">
                      Review and approve
                    </a>
                  </p>
                  <p style="font-size:12px;color:#9ca3af;">The vendor is waiting on your decision to continue the order.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(approver.getName()), intro, rows, link(items));
    }

    /** Waiting on the MR's own review: the Orders page; waiting on a manager: the Approvals queue. */
    private String link(List<OrderDesign> items) {
        boolean anyFinal = items.stream().anyMatch(design -> design.getStatus() == OrderDesignStatus.PENDING_FINAL_APPROVAL);
        return frontendBaseUrl + (anyFinal ? "/approvals" : "/orders");
    }
}
