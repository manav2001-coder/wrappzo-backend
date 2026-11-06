package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderApprovalStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.OrderRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/** The two order-approval emails. They name the order, gift and recipient -- never the vendor. */
@Service
public class OrderApprovalNotificationService {

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
        return order.getGift() != null ? order.getGift().getName() : "Gift";
    }
}
