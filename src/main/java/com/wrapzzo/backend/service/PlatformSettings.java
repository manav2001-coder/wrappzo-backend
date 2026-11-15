package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.PlatformSetting;
import com.wrapzzo.backend.repository.PlatformSettingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Typed access to the platform-wide settings a Super Admin changes from the app. They live in the database, so every server sees the
 * same values; each server keeps what it read for a few seconds, so the hot paths that ask (an email about to be queued) cost no query,
 * and a change reaches every server within {@link #REFRESH}. The server that handled the change follows at once.
 */
@Component
public class PlatformSettings {

    /** Whether the order-workflow emails are sent. Absent means on: how the platform behaved before the setting existed. */
    public static final String WORKFLOW_EMAILS_ENABLED = "notification_emails_enabled";

    static final Duration REFRESH = Duration.ofSeconds(5);
    private static final Logger log = LoggerFactory.getLogger(PlatformSettings.class);

    private record Read(Optional<String> value, Instant at) {
    }

    private final PlatformSettingRepository repository;
    private final Map<String, Read> reads = new ConcurrentHashMap<>();

    public PlatformSettings(PlatformSettingRepository repository) {
        this.repository = repository;
    }

    public boolean workflowEmailsEnabled() {
        return value(WORKFLOW_EMAILS_ENABLED).map(Boolean::parseBoolean).orElse(true);
    }

    /** The saved setting with who changed it and when -- for the screen that edits it; always fresh. */
    @Transactional(readOnly = true)
    public Optional<PlatformSetting> find(String key) {
        return repository.findById(key);
    }

    @Transactional
    public PlatformSetting save(String key, String value, String changedBy) {
        PlatformSetting setting = repository.findById(key).orElseGet(() -> new PlatformSetting(key, value, changedBy));
        setting.change(value, changedBy);
        PlatformSetting saved = repository.save(setting);
        reads.put(key, new Read(Optional.of(value), Instant.now()));
        return saved;
    }

    private Optional<String> value(String key) {
        Read read = reads.get(key);
        if (read != null && Instant.now().isBefore(read.at().plus(REFRESH))) {
            return read.value();
        }
        try {
            Optional<String> fresh = repository.findById(key).map(PlatformSetting::getValue);
            reads.put(key, new Read(fresh, Instant.now()));
            return fresh;
        } catch (RuntimeException e) {
            // A database hiccup must not stop the workflow or silence it: carry on with the last value, and look again shortly.
            log.warn("Could not read the platform setting {}; using the last known value: {}", key, e.getMessage());
            Optional<String> last = read == null ? Optional.empty() : read.value();
            reads.put(key, new Read(last, Instant.now()));
            return last;
        }
    }

    /** Forget what this server has read (tests). */
    void forgetReads() {
        reads.clear();
    }
}
