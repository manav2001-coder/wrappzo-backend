package com.wrapzzo.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The real servers, over HTTP: health on the public address, and the metrics only on the private management port --
 * with the numbers an operator needs to alert on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class MonitoringHttpTest {

    @Value("${local.server.port}") private int appPort;
    @Value("${local.management.port}") private int managementPort;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void thePublicAddressAnswersHealthWithoutSigningInAndSaysNothingElse() throws Exception {
        HttpResponse<String> response = get(appPort, "/api/public/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("jdbc").doesNotContain("postgres");
    }

    @Test
    void metricsAreNotAvailableOnThePublicAddress() throws Exception {
        assertThat(managementPort).isNotEqualTo(appPort);
        assertThat(get(appPort, "/actuator/prometheus").statusCode()).isNotEqualTo(200);
        assertThat(get(appPort, "/actuator/metrics").statusCode()).isNotEqualTo(200);
    }

    @Test
    void theManagementPortServesMetricsForRequestsThePoolAndTheQueues() throws Exception {
        get(appPort, "/api/public/health"); // at least one request on the books

        HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus");

        assertThat(scrape.statusCode()).isEqualTo(200);
        String metrics = scrape.body();
        assertThat(metrics).contains("http_server_requests_seconds_bucket"); // latency, by endpoint and status
        assertThat(metrics).containsPattern("hikaricp_connections_max\\{[^}]*pool=\"wrapzzo-db\"[^}]*\\} 30\\.0");
        assertThat(metrics).contains("hikaricp_connections_active");
        assertThat(metrics).contains("wrapzzo_email_outbox_pending");
        assertThat(metrics).contains("wrapzzo_email_outbox_failed");
        assertThat(metrics).contains("wrapzzo_email_outbox_oldest_due_seconds");
        assertThat(metrics).contains("wrapzzo_import_jobs_open");
        assertThat(metrics).contains("application=\"wrapzzo-backend\"");
    }

    @Test
    void theManagementPortAnswersLivenessAndReadinessProbes() throws Exception {
        assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);
    }
}
