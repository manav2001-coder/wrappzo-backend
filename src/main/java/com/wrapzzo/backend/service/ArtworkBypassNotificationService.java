package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Tells the people whose approval was skipped that a senior approved the proof in their place -- so nobody is surprised to find
 * it gone from their queue. Like the other artwork emails it names the order, the gift and the version, never the vendor.
 */
@Service
public class ArtworkBypassNotificationService {

    private final OrderDesignRepository designs;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final String frontendBaseUrl;

    public ArtworkBypassNotificationService(OrderDesignRepository designs, UserRepository users, EmailSender emailSender,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.designs = designs;
        this.users = users;
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional(readOnly = true)
    public void notifySkipped(ArtworkBypassedEvent event) {
        Optional<OrderDesign> design = designs.findById(event.designId());
        Optional<User> by = users.findById(event.bypassedById());
        if (design.isEmpty() || by.isEmpty()) {
            return;
        }
        String order = "ORD-" + design.get().getOrder().getOrderNo();
        String gift = design.get().getOrder().getGift() != null ? design.get().getOrder().getGift().getName() : "a PEL";
        for (User skipped : users.findAllById(event.skippedUserIds())) {
            if (skipped.getStatus() != UserStatus.ACTIVE || skipped.getEmail() == null || skipped.getEmail().isBlank()) {
                continue;
            }
            String reason = event.comment() == null || event.comment().isBlank()
                    ? ""
                    : "<p>Their note: <em>" + HtmlUtils.htmlEscape(event.comment()) + "</em></p>";
            emailSender.send(skipped.getEmail(), "Artwork proof " + order + " was approved in your place", """
                    <p>Hi %s,</p>
                    <p>%s approved the artwork proof for order %s (%s, version %d) in your place, so it has moved on and no longer needs your approval.</p>
                    %s
                    <p><a href="%s/approvals">Open Approvals</a></p>
                    """.formatted(HtmlUtils.htmlEscape(skipped.getName()), HtmlUtils.htmlEscape(by.get().getName()), order,
                    HtmlUtils.htmlEscape(gift), design.get().getVersionNo(), reason, frontendBaseUrl));
        }
    }
}
