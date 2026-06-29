package com.dbfleetops.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();

        logger = (Logger) LoggerFactory.getLogger(
                RequestLoggingFilter.class
        );

        appender = new ListAppender<>();
        appender.start();

        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsSuccessfulRequestAtInfoLevel()
            throws ServletException, IOException {

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        "/api/v1/databases/default/health"
                );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        executeFilter(
                request,
                response,
                200
        );

        ILoggingEvent event =
                appender.list.getFirst();

        assertThat(event.getLevel())
                .isEqualTo(Level.INFO);

        assertThat(event.getFormattedMessage())
                .contains(
                        "http_request_completed",
                        "method=GET",
                        "path=/api/v1/databases/default/health",
                        "status=200",
                        "durationMs="
                );
    }

    @Test
    void logsClientErrorAtWarnLevel()
            throws ServletException, IOException {

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        "/api/v1/not-exists"
                );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        executeFilter(
                request,
                response,
                404
        );

        ILoggingEvent event =
                appender.list.getFirst();

        assertThat(event.getLevel())
                .isEqualTo(Level.WARN);

        assertThat(event.getFormattedMessage())
                .contains(
                        "status=404"
                );
    }

    @Test
    void logsServerErrorAtErrorLevel()
            throws ServletException, IOException {

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        "/api/v1/test/error"
                );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        executeFilter(
                request,
                response,
                500
        );

        ILoggingEvent event =
                appender.list.getFirst();

        assertThat(event.getLevel())
                .isEqualTo(Level.ERROR);

        assertThat(event.getFormattedMessage())
                .contains(
                        "status=500"
                );
    }

    private void executeFilter(
            MockHttpServletRequest request,
            MockHttpServletResponse response,
            int responseStatus
    ) throws ServletException, IOException {

        FilterChain filterChain =
                (servletRequest, servletResponse) -> {
                    HttpServletResponse httpResponse =
                            (HttpServletResponse) servletResponse;

                    httpResponse.setStatus(
                            responseStatus
                    );
                };

        filter.doFilter(
                request,
                response,
                filterChain
        );
    }
}