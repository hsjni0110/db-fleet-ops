package com.dbfleetops.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Pattern VALID_REQUEST_ID =
            Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = resolveRequestId(
                request.getHeader(REQUEST_ID_HEADER)
        );

        request.setAttribute(
                REQUEST_ID_ATTRIBUTE,
                requestId
        );

        response.setHeader(
                REQUEST_ID_HEADER,
                requestId
        );

        MDC.put(
                REQUEST_ID_MDC_KEY,
                requestId
        );

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private String resolveRequestId(String incomingRequestId) {
        if (
                incomingRequestId != null
                && VALID_REQUEST_ID
                        .matcher(incomingRequestId)
                        .matches()
        ) {
            return incomingRequestId;
        }

        return UUID.randomUUID().toString();
    }
}