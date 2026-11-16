package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The two emails say what a person needs: the link and how long it lasts, and when their password changed and what to do if it was not them. */
class PasswordEmailListenerTest {

    @Test
    void theResetEmailCarriesTheLinkTheLifetimeAndAReassurance() {
        String body = PasswordEmailListener.resetBody(new PasswordResetRequestedEvent("a@b.com", "Rohit", "https://workspace.wrappzo.com/reset-password?token=abc", 30));
        assertThat(body).contains("Hi Rohit").contains("href=\"https://workspace.wrappzo.com/reset-password?token=abc\"").contains("30 minutes").contains("ignore this email");
    }

    @Test
    void theChangedEmailSaysWhenInIndiaTimeAndWhatToDoIfItWasNotThem() {
        String body = PasswordEmailListener.changedBody(new PasswordChangedEvent("a@b.com", "Rohit", Instant.parse("2026-11-16T10:00:00Z")));
        assertThat(body).contains("Hi Rohit").contains("16 Nov 2026, 3:30 PM").contains("tell your admin").contains("support@wrappzo.com");
    }
}
