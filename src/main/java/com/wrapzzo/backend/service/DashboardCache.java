package com.wrapzzo.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A few seconds of memory for dashboard answers, per viewer, so a thousand people opening the app at nine o'clock cost the database a
 * fraction of the queries. Figures can lag by up to the TTL; the live feeds elsewhere (orders, approvals) are not cached. Each server
 * keeps its own copy. Set wrappzo.dashboard.cache-seconds=0 to turn it off.
 */
@Component
public class DashboardCache {

    private static final int MAX_ENTRIES = 5_000;

    private record Entry(Instant at, Object value) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public DashboardCache(@Value("${wrappzo.dashboard.cache-seconds:30}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> load) {
        if (ttlSeconds <= 0) {
            return load.get();
        }
        Entry hit = entries.get(key);
        if (hit != null && Duration.between(hit.at(), Instant.now()).getSeconds() < ttlSeconds) {
            return (T) hit.value();
        }
        T value = load.get();
        if (entries.size() >= MAX_ENTRIES) {
            Instant cutoff = Instant.now().minusSeconds(ttlSeconds);
            entries.values().removeIf(e -> e.at().isBefore(cutoff));
            if (entries.size() >= MAX_ENTRIES) {
                entries.clear();
            }
        }
        entries.put(key, new Entry(Instant.now(), value));
        return value;
    }
}
