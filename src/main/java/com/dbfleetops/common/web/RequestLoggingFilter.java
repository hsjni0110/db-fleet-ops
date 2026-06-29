package com.dbfleetops.common.web;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter
        extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RequestLoggingFilter.class
            );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long startedAt = System.nanoTime();

        try {
            filterChain.doFilter(
                    request,
                    response
            );
        } finally {
            long durationMs = elapsedMillis(startedAt);

            logRequestCompleted(
                    request,
                    response,
                    durationMs
            );
        }
    }

    private void logRequestCompleted(
            HttpServletRequest request,
            HttpServletResponse response,
            long durationMs
    ) {
        int status = response.getStatus();

        if (status >= 500) {
            log.error(
                    "http_request_completed"
                            + " method={}"
                            + " path={}"
                            + " status={}"
                            + " durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    durationMs
            );

            return;
        }

        if (status >= 400) {
            log.warn(
                    "http_request_completed"
                            + " method={}"
                            + " path={}"
                            + " status={}"
                            + " durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    durationMs
            );

            return;
        }

        log.info(
                "http_request_completed"
                        + " method={}"
                        + " path={}"
                        + " status={}"
                        + " durationMs={}",
                request.getMethod(),
                request.getRequestURI(),
                status,
                durationMs
        );
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
    }
}