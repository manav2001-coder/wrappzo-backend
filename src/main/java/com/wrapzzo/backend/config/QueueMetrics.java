package com.wrapzzo.backend.config;

import com.wrapzzo.backend.service.EmailOutboxStore;
import com.wrapzzo.backend.service.ImportJobStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * What the app is holding for later, as numbers a dashboard can alert on. Read when the metrics are scraped, from the
 * database, so every replica reports the same thing. A reading that fails is reported as NaN rather than breaking the scrape.
 */
@Component
@Slf4j
public class QueueMetrics {

    public QueueMetrics(MeterRegistry registry, EmailOutboxStore emails, ImportJobStore imports) {
        gauge(registry, "wrapzzo.email.outbox.pending", "Emails waiting to be sent or retried", () -> emails.count("PENDING"));
        gauge(registry, "wrapzzo.email.outbox.failed", "Emails given up on after every retry", () -> emails.count("FAILED"));
        gauge(registry, "wrapzzo.email.outbox.oldest.due.seconds", "How long the longest-waiting due email has waited -- grows when sending is stuck", emails::oldestDueAgeSeconds);
        gauge(registry, "wrapzzo.import.jobs.open", "Bulk uploads queued or running", imports::countOpen);
    }

    private void gauge(MeterRegistry registry, String name, String description, Supplier<Number> reading) {
        Gauge.builder(name, () -> {
                    try {
                        return reading.get().doubleValue();
                    } catch (RuntimeException e) {
                        log.warn("Could not read {} for the metrics: {}", name, e.getMessage());
                        return Double.NaN;
                    }
                })
                .description(description)
                .register(registry);
    }
}
