package com.wrapzzo.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrapzzo.backend.exception.EmailDeliveryException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Talks to Resend's HTTP API directly -- no SDK needed for something this small. */
@Service
@Slf4j
public class ResendEmailSender implements EmailSender {

    private static final URI RESEND_ENDPOINT = URI.create("https://api.resend.com/emails");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String fromAddress;

    public ResendEmailSender(@Value("${resend.api-key}") String apiKey, @Value("${resend.from-address}") String fromAddress) {
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String html) {
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("from", fromAddress, "to", List.of(to), "subject", subject, "html", html));
            HttpRequest request = HttpRequest.newBuilder(RESEND_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                // The client only ever sees a generic 502 (GlobalExceptionHandler) -- log the
                // real reason here, since that detail still matters for debugging.
                log.warn("Resend rejected an email to {} (status {}): {}", to, response.statusCode(), response.body());
                throw new EmailDeliveryException(
                        "Resend rejected the email (status %d): %s".formatted(response.statusCode(), response.body()));
            }
        } catch (IOException e) {
            log.warn("Failed to reach Resend while emailing {}", to, e);
            throw new EmailDeliveryException("Failed to reach Resend", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while emailing {} via Resend", to, e);
            throw new EmailDeliveryException("Interrupted while sending email via Resend", e);
        }
    }
}
