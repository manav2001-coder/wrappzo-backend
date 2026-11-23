package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.exception.ServerBusyException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** What keeps a rush of dashboards from using every database connection. No database: transactions are counted, not opened. */
class DashboardCacheTest {

    /** Counts transactions begun, so a test can tell whether an answer touched the database at all. */
    private static final class CountingTransactions implements PlatformTransactionManager {
        final AtomicInteger begun = new AtomicInteger();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            begun.incrementAndGet();
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    @Test
    void anAnswerInMemoryOpensNoTransaction() {
        CountingTransactions tx = new CountingTransactions();
        DashboardCache cache = new DashboardCache(30, 4, 5, tx);
        AtomicInteger loads = new AtomicInteger();

        String first = cache.get("k", () -> "v" + loads.incrementAndGet());
        String second = cache.get("k", () -> "v" + loads.incrementAndGet());

        assertThat(first).isEqualTo("v1");
        assertThat(second).isEqualTo("v1");
        assertThat(loads).hasValue(1);
        assertThat(tx.begun).as("only the miss opened a transaction").hasValue(1);
    }

    @Test
    void theSameViewerAskingTwiceAtOnceWaitsForOneAnswer() throws Exception {
        DashboardCache cache = new DashboardCache(30, 4, 5, new CountingTransactions());
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<String>> answers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                answers.add(pool.submit(() -> cache.get("same", () -> {
                    loads.incrementAndGet();
                    await(release);
                    return "answer";
                })));
            }
            Thread.sleep(200); // let all five arrive while the first is still working
            release.countDown();
            for (Future<String> answer : answers) {
                assertThat(answer.get(5, TimeUnit.SECONDS)).isEqualTo("answer");
            }
            assertThat(loads).as("five askers, one computation").hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void onlyAFewDashboardsAreWorkedOutAtATime() throws Exception {
        DashboardCache cache = new DashboardCache(30, 2, 10, new CountingTransactions());
        AtomicInteger running = new AtomicInteger();
        AtomicInteger mostAtOnce = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> answers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String key = "viewer-" + i;
                answers.add(pool.submit(() -> cache.get(key, () -> {
                    mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
                    sleep(100);
                    running.decrementAndGet();
                    return key;
                })));
            }
            for (Future<String> answer : answers) {
                answer.get(10, TimeUnit.SECONDS);
            }
            assertThat(mostAtOnce.get()).as("the limit holds").isLessThanOrEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void waitingTooLongForATurnIsAClearBusyAnswer() throws Exception {
        DashboardCache cache = new DashboardCache(30, 1, 1, new CountingTransactions());
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> slow = pool.submit(() -> cache.get("slow", () -> {
                await(release);
                return "done";
            }));
            Thread.sleep(200);
            assertThatThrownBy(() -> cache.get("other", () -> "x"))
                    .isInstanceOf(ServerBusyException.class)
                    .hasMessageContaining("try again");
            release.countDown();
            assertThat(slow.get(5, TimeUnit.SECONDS)).isEqualTo("done");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFailedComputationIsNotRemembered() {
        DashboardCache cache = new DashboardCache(30, 4, 5, new CountingTransactions());
        assertThatThrownBy(() -> cache.get("k", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(cache.<String>get("k", () -> "second try")).isEqualTo("second try");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
