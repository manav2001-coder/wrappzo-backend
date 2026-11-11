package com.wrapzzo.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SlowRequestLogFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(SlowRequestLogFilter.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void capture() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void release() {
        logger.detachAppender(logs);
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/page");
        request.setQueryString("search=dr+sharma");
        return request;
    }

    @Test
    void aSlowRequestIsLoggedWithItsMethodPathStatusAndTimeButNotWhatWasSearchedFor() throws Exception {
        FilterChain slow = (req, res) -> {
            pause(30);
            ((MockHttpServletResponse) res).setStatus(200);
        };

        new SlowRequestLogFilter(10).doFilter(request(), new MockHttpServletResponse(), slow);

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
        String line = logs.list.get(0).getFormattedMessage();
        assertThat(line).contains("GET /api/orders/page").contains("(status 200)").containsPattern("took \\d+ ms").doesNotContain("sharma");
    }

    @Test
    void aFastRequestIsNotLogged() throws Exception {
        new SlowRequestLogFilter(60_000).doFilter(request(), new MockHttpServletResponse(), (req, res) -> { });

        assertThat(logs.list).isEmpty();
    }

    @Test
    void aRequestThatFailsIsStillTimedAndTheFailureStillPropagates() {
        FilterChain failing = (req, res) -> {
            pause(20);
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> new SlowRequestLogFilter(10).doFilter(request(), new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(logs.list).hasSize(1);
    }
}
