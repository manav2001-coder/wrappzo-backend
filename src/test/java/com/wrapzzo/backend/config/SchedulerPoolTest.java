package com.wrapzzo.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The background tasks (bulk-import polling, the email outbox) must not share one thread: the outbox draining a backlog of emails
 * once kept every bulk upload waiting for minutes before it even started. Spring's default is one thread, so the setting must stay.
 */
class SchedulerPoolTest {

    @Test
    void backgroundTasksDoNotShareOneThread() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(in);
        }
        String raw = properties.getProperty("spring.task.scheduling.pool.size");
        assertThat(raw).as("spring.task.scheduling.pool.size is set").isNotBlank();
        int size = Integer.parseInt(raw.replaceAll("^\\$\\{[A-Z_]+:(\\d+)}$", "$1"));
        assertThat(size).isGreaterThanOrEqualTo(3);
    }
}
