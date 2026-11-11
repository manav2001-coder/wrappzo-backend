package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/** Emails to the load-test franchise's people are dropped before they reach the outbox -- and nobody else's are. */
class OutboxEmailSenderTest {

    private final EmailOutboxStore outbox = mock(EmailOutboxStore.class);
    private final OutboxEmailSender sender = new OutboxEmailSender(outbox, "loadtest.invalid");

    @Test
    void anEmailToAnAddressAtASuppressedDomainIsNeitherQueuedNorSent() {
        sender.send("am0001@loadtest.invalid", "Set up your account", "<p>hi</p>");
        sender.send("AM0002@LoadTest.Invalid", "Set up your account", "<p>hi</p>");
        sender.send("am0003@mail.loadtest.invalid", "Set up your account", "<p>hi</p>");

        verify(outbox, never()).enqueue(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void everyoneElseStillGetsTheirEmail() {
        sender.send("asha@alembic.com", "Hello", "<p>hi</p>");
        sender.send("someone@notloadtest.invalid", "Hello", "<p>hi</p>");
        sender.send("loadtest.invalid@gmail.com", "Hello", "<p>hi</p>");

        verify(outbox).enqueue("asha@alembic.com", "Hello", "<p>hi</p>");
        verify(outbox).enqueue("someone@notloadtest.invalid", "Hello", "<p>hi</p>");
        verify(outbox).enqueue("loadtest.invalid@gmail.com", "Hello", "<p>hi</p>");
    }

    @Test
    void moreDomainsCanBeAddedAndBlanksAreIgnored() {
        OutboxEmailSender more = new OutboxEmailSender(outbox, " loadtest.invalid , staging.example.com ,, ");

        assertThat(more.isSuppressed("x@staging.example.com")).isTrue();
        assertThat(more.isSuppressed("x@loadtest.invalid")).isTrue();
        assertThat(more.isSuppressed("x@example.com")).isFalse();
        assertThat(more.isSuppressed(null)).isFalse();
        assertThat(more.isSuppressed("")).isFalse();
    }
}
