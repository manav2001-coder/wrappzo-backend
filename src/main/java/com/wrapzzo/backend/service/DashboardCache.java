package com.wrapzzo.backend.service;

import com.wrapzzo.backend.exception.ServerBusyException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A few seconds of memory for dashboard answers, per viewer, so a thousand people opening the app at nine o'clock cost the database a
 * fraction of the queries. Figures can lag by up to the TTL; the live feeds elsewhere (orders, approvals) are not cached. Each server
 * keeps its own copy. Set wrappzo.dashboard.cache-seconds=0 to turn it off.
 *
 * <p>Three things keep a rush of dashboards from hurting the rest of the app:
 * <ul>
 *   <li>The database work (and its transaction) happens only on a miss, inside {@link #get}: an answer that is in memory costs no database
 *       connection, so cached dashboards keep answering even when the pool is full.</li>
 *   <li>The same viewer asking again while their answer is being worked out waits for that one answer instead of starting another.</li>
 *   <li>At most {@code max-concurrent-loads} dashboards are worked out at a time, so heavy ones queue here rather than use every
 *       connection and starve orders and approvals. One that waits longer than {@code max-wait-seconds} is answered "busy, try again".</li>
 * </ul>
 */
@Component
public class DashboardCache {

    private static final int MAX_ENTRIES = 5_000;

    private record Entry(Instant at, Object value) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    private final long ttlSeconds;
    private final Semaphore slots;
    private final long waitSeconds;
    private final TransactionTemplate transaction;

    public DashboardCache(
            @Value("${wrappzo.dashboard.cache-seconds:30}") long ttlSeconds,
            @Value("${wrappzo.dashboard.max-concurrent-loads:6}") int maxConcurrentLoads,
            @Value("${wrappzo.dashboard.max-wait-seconds:20}") long waitSeconds,
            PlatformTransactionManager transactionManager) {
        this.ttlSeconds = ttlSeconds;
        this.slots = new Semaphore(Math.max(1, maxConcurrentLoads), true);
        this.waitSeconds = waitSeconds;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setReadOnly(true);
    }

    /** The cached answer for {@code key}, or {@code load} run in a read-only transaction (and remembered). Do not open a transaction around this call. */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> load) {
        if (ttlSeconds <= 0) {
            return inTransaction(load);
        }
        T hit = fresh(key);
        if (hit != null) {
            return hit;
        }
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            return (T) await(running);
        }
        try {
            T value = loadWhenItsTurn(key, load);
            mine.complete(value);
            return value;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key);
        }
    }

    private <T> T loadWhenItsTurn(String key, Supplier<T> load) {
        try {
            if (!slots.tryAcquire(waitSeconds, TimeUnit.SECONDS)) {
                throw busy();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy();
        }
        try {
            T again = fresh(key); // someone may have stored it while this one waited for its turn
            if (again != null) {
                return again;
            }
            T value = inTransaction(load);
            remember(key, value);
            return value;
        } finally {
            slots.release();
        }
    }

    private <T> T inTransaction(Supplier<T> load) {
        return transaction.execute(status -> load.get());
    }

    @SuppressWarnings("unchecked")
    private <T> T fresh(String key) {
        Entry hit = entries.get(key);
        return hit != null && Duration.between(hit.at(), Instant.now()).getSeconds() < ttlSeconds ? (T) hit.value() : null;
    }

    private void remember(String key, Object value) {
        if (entries.size() >= MAX_ENTRIES) {
            Instant cutoff = Instant.now().minusSeconds(ttlSeconds);
            entries.values().removeIf(e -> e.at().isBefore(cutoff));
            if (entries.size() >= MAX_ENTRIES) {
                entries.clear();
            }
        }
        entries.put(key, new Entry(Instant.now(), value));
    }

    private Object await(CompletableFuture<Object> running) {
        try {
            return running.get(waitSeconds + 5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException r) {
                throw r;
            }
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw busy();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy();
        }
    }

    private static ServerBusyException busy() {
        return new ServerBusyException("The dashboard is busy working out other people's figures right now -- please try again in a few seconds.");
    }
}
