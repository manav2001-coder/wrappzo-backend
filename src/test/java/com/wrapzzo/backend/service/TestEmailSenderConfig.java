package com.wrapzzo.backend.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real Resend-backed EmailSender in tests -- Resend is a real
 * third-party paid API, not our own infrastructure like Postgres/Redis/MinIO,
 * so tests record what would have been sent instead of actually sending it.
 */
@TestConfiguration
public class TestEmailSenderConfig {

    @Bean
    @Primary
    public RecordingEmailSender emailSender() {
        return new RecordingEmailSender();
    }

    public static class RecordingEmailSender implements EmailSender {
        public final List<Sent> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(String to, String subject, String html) {
            sent.add(new Sent(to, subject, html));
        }

        public record Sent(String to, String subject, String html) {
        }
    }
}
