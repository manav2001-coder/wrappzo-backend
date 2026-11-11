package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.security.PublicEndpoint;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * For a load balancer or an uptime check on the public address: 200 when the app can reach its database, 503 when it
 * cannot. It says nothing else about the system -- the detailed numbers are on the private management port.
 */
@RestController
@RequestMapping("/api/public/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbc;

    @GetMapping
    @PublicEndpoint
    public ResponseEntity<Map<String, String>> health() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN", "error", "The database did not answer"));
        }
    }
}
