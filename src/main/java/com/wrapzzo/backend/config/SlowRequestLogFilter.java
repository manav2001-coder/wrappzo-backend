package com.wrapzzo.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs a WARN line for any request that takes longer than {@code monitoring.slow-request-ms}, naming the method, path,
 * status and time -- so "the orders screen is slow" can be found in the logs, not guessed at. The query string is left
 * out on purpose: it carries what people searched for.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SlowRequestLogFilter extends OncePerRequestFilter {

    private final long thresholdMs;

    public SlowRequestLogFilter(@Value("${monitoring.slow-request-ms:1500}") long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long tookMs = (System.nanoTime() - started) / 1_000_000;
            if (tookMs >= thresholdMs) {
                log.warn("Slow request: {} {} took {} ms (status {})", request.getMethod(), request.getRequestURI(), tookMs, response.getStatus());
            }
        }
    }
}
