package com.wrapzzo.backend.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Backs the invite-email dispatch after a bulk user import -- sending is a per-recipient
 * HTTP call to Resend (no batch-send endpoint), so a large import shouldn't make the
 * caller wait on hundreds/thousands of those sequentially. A modest, bounded pool: this
 * is I/O-bound network work, not CPU work, and unbounded concurrency would just hammer
 * Resend's own rate limit instead of anything on our side.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("email-invite-");
        executor.initialize();
        return executor;
    }
}
