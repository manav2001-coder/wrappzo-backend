package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * The emails a vendor (printer) gets as an order moves through its hands: a new order, the same order taken away, a proof sent
 * back for changes, a proof approved so it can ship. Internal steps -- a proof climbing the customer's approval levels --
 * are not the vendor's business and send nothing. Each email says what to do next and links to the order in the vendor portal;
 * it names the company and the gift (the vendor sees both in the portal) and nothing about who inside the customer decided.
 */
@Service
public class VendorNotificationService {

    private final OrderRepository orders;
    private final OrderDesignRepository designs;
    private final VendorRepository vendors;
    private final EmailSender emailSender;
    private final String frontendBaseUrl;

    public VendorNotificationService(OrderRepository orders, OrderDesignRepository designs, VendorRepository vendors, EmailSender emailSender,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.orders = orders;
        this.designs = designs;
        this.vendors = vendors;
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** A new order for the vendor; and, if it was taken from another vendor, a note to that one. */
    @Transactional(readOnly = true)
    public void orderAssigned(UUID orderId, UUID vendorId, UUID previousVendorId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        Vendor vendor = vendors.findById(vendorId).orElse(null);
        if (reachable(vendor)) {
            boolean needsArtwork = order.getGift() != null && order.getGift().isArtworkRequired();
            String next = needsArtwork
                    ? "Upload an artwork proof so the customer can approve it. You can dispatch once it is approved."
                    : "No artwork proof is needed: produce it and mark it dispatched when it ships.";
            emailSender.send(vendor.getContactEmail(), "New order " + code(order) + ": " + gift(order) + " x" + order.getQuantity(),
                    body(vendor, "A new order has been assigned to you.", order, next, "Open the order", order));
        }
        if (previousVendorId != null && !previousVendorId.equals(vendorId)) {
            Vendor previous = vendors.findById(previousVendorId).orElse(null);
            if (reachable(previous)) {
                emailSender.send(previous.getContactEmail(), "Order " + code(order) + " is no longer assigned to you",
                        body(previous, "Order " + code(order) + " has been assigned to another vendor, so there is nothing more for you to do on it.",
                                order, null, null, null));
            }
        }
    }

    /** Sent back for changes (with the reviewer's comment), or approved so it may ship. */
    @Transactional(readOnly = true)
    public void proofDecided(UUID designId, boolean approved) {
        OrderDesign design = designs.findById(designId).orElse(null);
        if (design == null) {
            return;
        }
        Vendor vendor = design.getUploadedBy();
        if (!reachable(vendor)) {
            return;
        }
        Order order = design.getOrder();
        String proof = "proof v" + design.getVersionNo() + " for " + code(order);
        if (approved) {
            emailSender.send(vendor.getContactEmail(), "Approved: " + proof,
                    body(vendor, "Your " + proof + " was approved.", order, "You can pack the order and mark it dispatched.", "Open the order", order));
        } else {
            String comment = design.getReviewComment() == null || design.getReviewComment().isBlank()
                    ? "No comment was left."
                    : "Their note: “" + HtmlUtils.htmlEscape(design.getReviewComment().trim()) + "”";
            emailSender.send(vendor.getContactEmail(), "Changes requested on your " + proof,
                    body(vendor, "The customer sent your " + proof + " back for changes.", order,
                            comment + "<br>Upload a corrected proof to continue.", "Open the order", order));
        }
    }

    private static boolean reachable(Vendor vendor) {
        return vendor != null && vendor.getStatus() == VendorStatus.ACTIVE && vendor.getContactEmail() != null && !vendor.getContactEmail().isBlank();
    }

    private static String code(Order order) {
        return "ORD-" + order.getOrderNo();
    }

    private static String gift(Order order) {
        return order.getGift() != null ? order.getGift().getName() : "PEL";
    }

    private String body(Vendor vendor, String headline, Order order, String next, String button, Order linked) {
        String link = linked == null ? "" : """
                <p style="text-align:center;margin:28px 0;">
                  <a href="%s" style="display:inline-block;padding:12px 28px;background:#4f46e5;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600;font-size:15px;">%s</a>
                </p>""".formatted(frontendBaseUrl + "/vendor/orders/" + linked.getId(), button);
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;">
                  <p style="font-size:15px;color:#111827;">Hi %s,</p>
                  <p style="font-size:15px;color:#111827;line-height:1.6;">%s</p>
                  <p style="font-size:14px;color:#111827;line-height:1.6;"><strong>%s</strong> &middot; %s &middot; quantity %d &middot; %s</p>
                  %s
                  %s
                </div>
                """.formatted(HtmlUtils.htmlEscape(vendor.getName()), headline, code(order), HtmlUtils.htmlEscape(gift(order)), order.getQuantity(),
                HtmlUtils.htmlEscape(order.getCompany().getName()),
                next == null ? "" : "<p style=\"font-size:14px;color:#374151;line-height:1.6;\">" + next + "</p>", link);
    }
}
