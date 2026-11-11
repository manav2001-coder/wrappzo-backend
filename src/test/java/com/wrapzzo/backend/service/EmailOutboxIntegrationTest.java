package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrapzzo.backend.exception.EmailDeliveryException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real database, not @Transactional: the outbox's whole point is what is committed. Recipients carry a per-run tag so rows are ours. */
@SpringBootTest
class EmailOutboxIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EmailOutboxStore store;
    @Autowired private OutboxEmailSender sender;
    @Autowired private PlatformTransactionManager transactions;

    private final String tag = "outbox-" + UUID.randomUUID() + "-";
    private final List<String> delivered = new CopyOnWriteArrayList<>();
    private final AtomicBoolean providerUp = new AtomicBoolean(true);

    private final EmailTransport provider = (to, subject, html) -> {
        if (!providerUp.get()) throw new EmailDeliveryException("Resend rejected the email (status 503)");
        delivered.add(to);
    };

    private EmailOutboxDispatcher dispatcher() {
        return new EmailOutboxDispatcher(store, provider, 50);
    }

    private List<String> mine() {
        return delivered.stream().filter(to -> to.startsWith(tag)).toList();
    }

    private String status(String to) {
        return jdbc.queryForObject("SELECT status FROM email_outbox WHERE to_address = ?", String.class, to);
    }

    private void makeDueNow(String to) {
        jdbc.update("UPDATE email_outbox SET next_attempt_at = clock_timestamp() - interval '1 second' WHERE to_address = ?", to);
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM email_outbox WHERE to_address LIKE ?", tag + "%");
    }

    @Test
    void anEmailIsSavedThenSentAndMarkedSent() {
        sender.send(tag + "a@x.com", "Hello", "<p>hi</p>");
        assertThat(status(tag + "a@x.com")).isEqualTo("PENDING");

        dispatcher().dispatchOnce();

        assertThat(mine()).containsExactly(tag + "a@x.com");
        assertThat(status(tag + "a@x.com")).isEqualTo("SENT");
        dispatcher().dispatchOnce();
        assertThat(mine()).hasSize(1);
    }

    @Test
    void anEmailSavedInATransactionThatRollsBackIsNeverSent() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            sender.send(tag + "ghost@x.com", "Never", "<p>x</p>");
            tx.setRollbackOnly();
        });

        dispatcher().dispatchOnce();

        assertThat(mine()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE to_address = ?", Integer.class, tag + "ghost@x.com")).isZero();
    }

    @Test
    void whenTheProviderIsDownTheEmailWaitsAndIsSentOnceItIsBack() {
        providerUp.set(false);
        sender.send(tag + "b@x.com", "Hello", "<p>hi</p>");

        dispatcher().dispatchOnce();

        assertThat(mine()).isEmpty();
        assertThat(status(tag + "b@x.com")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT last_error FROM email_outbox WHERE to_address = ?", String.class, tag + "b@x.com"))
                .contains("status 503");
        // Not tried again straight away -- it backs off.
        dispatcher().dispatchOnce();
        assertThat(jdbc.queryForObject("SELECT attempts FROM email_outbox WHERE to_address = ?", Integer.class, tag + "b@x.com")).isEqualTo(1);

        providerUp.set(true);
        makeDueNow(tag + "b@x.com");
        dispatcher().dispatchOnce();

        assertThat(mine()).containsExactly(tag + "b@x.com");
        assertThat(status(tag + "b@x.com")).isEqualTo("SENT");
    }

    @Test
    void anEmailThatKeepsFailingIsGivenUpOnAfterTheLastAttemptWithTheReason() {
        providerUp.set(false);
        sender.send(tag + "c@x.com", "Hello", "<p>hi</p>");

        for (int attempt = 1; attempt <= EmailOutboxDispatcher.MAX_ATTEMPTS; attempt++) {
            makeDueNow(tag + "c@x.com");
            dispatcher().dispatchOnce();
        }

        assertThat(status(tag + "c@x.com")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT attempts FROM email_outbox WHERE to_address = ?", Integer.class, tag + "c@x.com"))
                .isEqualTo(EmailOutboxDispatcher.MAX_ATTEMPTS);
        // Failed is final: it is not picked up again, even when due.
        providerUp.set(true);
        makeDueNow(tag + "c@x.com");
        dispatcher().dispatchOnce();
        assertThat(mine()).isEmpty();
    }

    @Test
    void replicasSendingAtTheSameTimeSendEachEmailExactlyOnce() throws Exception {
        int emails = 60;
        for (int i = 0; i < emails; i++) sender.send(tag + i + "@x.com", "Hello", "<p>hi</p>");
        int replicas = 4;
        ExecutorService pool = Executors.newFixedThreadPool(replicas);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (int r = 0; r < replicas; r++) {
                EmailOutboxDispatcher replica = new EmailOutboxDispatcher(store, provider, 7);
                runs.add(pool.submit(() -> {
                    go.await();
                    while (replica.dispatchOnce() > 0) {
                        // keep taking batches until nothing is due
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> run : runs) run.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(mine()).hasSize(emails).doesNotHaveDuplicates();
    }

    @Test
    void anEmailTakenByAReplicaThatDiedIsSentByAnotherOnceItsClaimRunsOut() {
        sender.send(tag + "d@x.com", "Hello", "<p>hi</p>");
        // A replica claims it, then dies before sending or recording anything.
        store.claimDue(100, EmailOutboxDispatcher.MAX_ATTEMPTS, Duration.ofMinutes(2));
        assertThat(status(tag + "d@x.com")).isEqualTo("SENDING");
        dispatcher().dispatchOnce();
        assertThat(mine()).isEmpty();

        jdbc.update("UPDATE email_outbox SET claimed_until = clock_timestamp() - interval '1 second' WHERE to_address = ?", tag + "d@x.com");
        dispatcher().dispatchOnce();

        assertThat(mine()).containsExactly(tag + "d@x.com");
        assertThat(status(tag + "d@x.com")).isEqualTo("SENT");
    }

    @Test
    void theCleanupJobRemovesOldSentEmailsAndKeepsRecentAndWaitingOnes() {
        sender.send(tag + "old@x.com", "Old", "<p>x</p>");
        sender.send(tag + "new@x.com", "New", "<p>x</p>");
        sender.send(tag + "waiting@x.com", "Waiting", "<p>x</p>");
        jdbc.update("UPDATE email_outbox SET status = 'SENT' WHERE to_address IN (?, ?)", tag + "old@x.com", tag + "new@x.com");
        jdbc.update("UPDATE email_outbox SET created_at = clock_timestamp() - interval '20 days' WHERE to_address = ?", tag + "old@x.com");

        new EmailOutboxCleanupJob(store).run();

        assertThat(jdbc.queryForList("SELECT to_address FROM email_outbox WHERE to_address LIKE ?", String.class, tag + "%"))
                .containsExactlyInAnyOrder(tag + "new@x.com", tag + "waiting@x.com");
    }
}
