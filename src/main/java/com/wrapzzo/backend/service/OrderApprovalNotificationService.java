package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderApprovalStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.OrderRepository;
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

/** The two order-approval emails. They name the order, gift and recipient -- never the vendor. */
@Service
@Slf4j
public class OrderApprovalNotificationService {

    static final int MAX_REMINDERS = 3;
    static final Duration REMINDER_EVERY = Duration.ofHours(24);

    private final OrderRepository orders;
    private final OrderApprovalService approvals;
    private final EmailSender emailSender;
    private final String frontendBaseUrl;

    public OrderApprovalNotificationService(OrderRepository orders, OrderApprovalService approvals, EmailSender emailSender,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.orders = orders;
        this.approvals = approvals;
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** An order just started waiting: tell the people who can decide it. */
    @Transactional(readOnly = true)
    public void notifyAwaiting(UUID orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null || order.getApprovalStatus() != OrderApprovalStatus.PENDING) {
            return;
        }
        for (User approver : approvals.awaitedApprovers(order)) {
            emailSender.send(approver.getEmail(), "Order " + label(order) + " is waiting for your approval", """
                    <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;">
                      <p style="font-size:15px;color:#111827;">Hi %s,</p>
                      <p style="font-size:15px;color:#111827;line-height:1.6;">%s has placed an order that needs your approval before it goes to production:</p>
                      <ul style="font-size:14px;color:#111827;line-height:1.5;padding-left:18px;">
                        <li><strong>%s</strong> &middot; %s &times; %d</li>
                        <li>For %s</li>
                      </ul>
                      <p style="text-align:center;margin:28px 0;">
                        <a href="%s/approvals" style="display:inline-block;padding:12px 28px;background:#4f46e5;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600;font-size:15px;">Review and approve</a>
                      </p>
                    </div>
                    """.formatted(HtmlUtils.htmlEscape(approver.getName()), HtmlUtils.htmlEscape(order.getOwner().getName()), label(order),
                    HtmlUtils.htmlEscape(giftOf(order)), order.getQuantity(), HtmlUtils.htmlEscape(String.valueOf(order.getRecipientName())), frontendBaseUrl));
        }
    }

    /** The daily job: each approver gets one email listing the orders that have waited a day (or another day) since the last one. */
    @Transactional
    public String sendDueReminders() {
        Instant now = Instant.now();
        Map<UUID, List<Order>> byApprover = new LinkedHashMap<>();
        Map<UUID, User> approvers = new LinkedHashMap<>();
        for (Order order : orders.findByApprovalStatusAndApprovalRemindersSentLessThanOrderByCreatedAtAsc(OrderApprovalStatus.PENDING, MAX_REMINDERS)) {
            Instant since = order.getApprovalLastReminderAt() != null ? order.getApprovalLastReminderAt() : order.getCreatedAt();
            if (since.plus(REMINDER_EVERY).isAfter(now)) {
                continue;
            }
            for (User approver : approvals.awaitedApprovers(order)) {
                approvers.putIfAbsent(approver.getId(), approver);
                byApprover.computeIfAbsent(approver.getId(), id -> new ArrayList<>()).add(order);
            }
        }
        int emails = 0;
        int failed = 0;
        Map<UUID, Order> reminded = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<Order>> entry : byApprover.entrySet()) {
            User approver = approvers.get(entry.getKey());
            List<Order> items = entry.getValue();
            try {
                String subject = items.size() == 1
                        ? "Reminder: order " + label(items.get(0)) + " is waiting for your approval"
                        : "Reminder: " + items.size() + " orders are waiting for your approval";
                emailSender.send(approver.getEmail(), subject, reminderBody(approver, items));
                emails++;
                items.forEach(order -> reminded.put(order.getId(), order));
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not send the order reminder to {}: {}", approver.getEmail(), e.getMessage());
            }
        }
        reminded.values().forEach(order -> {
            order.setApprovalRemindersSent(order.getApprovalRemindersSent() + 1);
            order.setApprovalLastReminderAt(now);
        });
        return emails + " reminder email(s) for " + reminded.size() + " order(s)" + (failed > 0 ? ", " + failed + " failed" : "");
    }

    private String reminderBody(User approver, List<Order> items) {
        StringBuilder rows = new StringBuilder();
        for (Order order : items) {
            rows.append("<li style=\"margin:6px 0;\"><strong>%s</strong> &middot; %s &times; %d &middot; for %s &middot; by %s</li>".formatted(
                    label(order), HtmlUtils.htmlEscape(giftOf(order)), order.getQuantity(),
                    HtmlUtils.htmlEscape(String.valueOf(order.getRecipientName())), HtmlUtils.htmlEscape(order.getOwner().getName())));
        }
        String intro = items.size() == 1 ? "An order is still waiting for your approval:" : items.size() + " orders are still waiting for your approval:";
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;">
                  <p style="font-size:15px;color:#111827;">Hi %s,</p>
                  <p style="font-size:15px;color:#111827;line-height:1.6;">%s</p>
                  <ul style="font-size:14px;color:#111827;line-height:1.5;padding-left:18px;">%s</ul>
                  <p style="text-align:center;margin:28px 0;">
                    <a href="%s/approvals" style="display:inline-block;padding:12px 28px;background:#4f46e5;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600;font-size:15px;">Review and approve</a>
                  </p>
                  <p style="font-size:12px;color:#9ca3af;">The order goes to production only once you approve it.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(approver.getName()), intro, rows, frontendBaseUrl);
    }

    /** The requester hears the outcome, with the approver's note when they left one. */
    @Transactional(readOnly = true)
    public void notifyDecided(UUID orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null || order.getOwner() == null
                || (order.getApprovalStatus() != OrderApprovalStatus.APPROVED && order.getApprovalStatus() != OrderApprovalStatus.REJECTED)) {
            return;
        }
        boolean approved = order.getApprovalStatus() == OrderApprovalStatus.APPROVED;
        String by = order.getApprovalDecidedBy() != null ? order.getApprovalDecidedBy().getName() : "Your approver";
        String note = order.getApprovalComment() == null || order.getApprovalComment().isBlank()
                ? ""
                : "<p style=\"font-size:14px;color:#111827;background:#f3f4f6;border-radius:8px;padding:10px 14px;\">&ldquo;%s&rdquo;</p>".formatted(HtmlUtils.htmlEscape(order.getApprovalComment()));
        emailSender.send(order.getOwner().getEmail(), "Order " + label(order) + (approved ? " was approved" : " was not approved"), """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;">
                  <p style="font-size:15px;color:#111827;">Hi %s,</p>
                  <p style="font-size:15px;color:#111827;line-height:1.6;">%s %s your order <strong>%s</strong> (%s &times; %d for %s).%s</p>
                  %s
                  <p style="text-align:center;margin:28px 0;">
                    <a href="%s/orders" style="display:inline-block;padding:12px 28px;background:#4f46e5;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600;font-size:15px;">View your orders</a>
                  </p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(order.getOwner().getName()), HtmlUtils.htmlEscape(by), approved ? "approved" : "did not approve", label(order),
                HtmlUtils.htmlEscape(giftOf(order)), order.getQuantity(), HtmlUtils.htmlEscape(String.valueOf(order.getRecipientName())),
                approved ? " It is now on its way to production." : " It has been cancelled.", note, frontendBaseUrl));
    }

    private static String label(Order order) {
        return "ORD-" + order.getOrderNo();
    }

    private static String giftOf(Order order) {
        return order.getGift() != null ? order.getGift().getName() : "PEL";
    }
}
