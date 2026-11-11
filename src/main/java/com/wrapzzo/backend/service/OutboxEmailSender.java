package com.wrapzzo.backend.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Saves the email to the outbox instead of sending it. Inside a transaction (a request, or a bulk import)
 * the email is saved with it: if the change rolls back, the email is never sent; if it commits, the email
 * is certain to go out, even if the server restarts first. Outside one it is saved on its own.
 *
 * Addresses at a suppressed domain are dropped here, never queued and never sent: the load-test franchise's people use
 * {@code @loadtest.invalid} (a domain that can never exist), and thousands of invite and order emails to it would count against the
 * sending quota and hurt the sender's reputation with bounces. More domains can be added with EMAIL_SUPPRESSED_DOMAINS (comma-separated).
 */
@Service
@Slf4j
public class OutboxEmailSender implements EmailSender {

    private final EmailOutboxStore outbox;
    private final List<String> suppressedDomains;

    public OutboxEmailSender(EmailOutboxStore outbox, @Value("${email.suppressed-domains:loadtest.invalid}") String suppressedDomains) {
        this.outbox = outbox;
        this.suppressedDomains = Arrays.stream(suppressedDomains.split(","))
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .toList();
    }

    @Override
    public void send(String to, String subject, String html) {
        if (isSuppressed(to)) {
            log.debug("Not emailing {}: its domain is suppressed", to);
            return;
        }
        outbox.enqueue(to, subject, html);
    }

    /** True for an address at a suppressed domain or any subdomain of one. */
    boolean isSuppressed(String address) {
        if (address == null) {
            return false;
        }
        int at = address.lastIndexOf('@');
        String domain = (at >= 0 ? address.substring(at + 1) : address).trim().toLowerCase(Locale.ROOT);
        return suppressedDomains.stream().anyMatch(suppressed -> domain.equals(suppressed) || domain.endsWith("." + suppressed));
    }
}
