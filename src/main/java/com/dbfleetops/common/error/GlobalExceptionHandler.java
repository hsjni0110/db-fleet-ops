package com.dbfleetops.common.error;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.dbfleetops.common.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GlobalExceptionHandler.class
            );

    private static final ZoneId DEFAULT_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private static final URI INTERNAL_SERVER_ERROR_TYPE =
            URI.create(
                    "https://db-fleetops.dev/problems/"
                            + "internal-server-error"
            );

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = resolveRequestId(request);

        log.error(
                "unexpected_server_error"
                        + " requestId={}"
                        + " method={}"
                        + " path={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        ApiErrorCode errorCode =
                ApiErrorCode.INTERNAL_SERVER_ERROR;

        // RFC 9457의 표준 필드와 사용자 정의 속성을 함께 담을 수 있다.
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        errorCode.status(),
                        errorCode.detail()
                );

        problem.setType(INTERNAL_SERVER_ERROR_TYPE);
        problem.setTitle(errorCode.title());
        problem.setInstance(
                URI.create(request.getRequestURI())
        );

        problem.setProperty(
                "errorCode",
                errorCode.code()
        );

        problem.setProperty(
                "requestId",
                requestId
        );

        problem.setProperty(
                "timestamp",
                OffsetDateTime.now(DEFAULT_ZONE_ID)
        );

        return problem;
    }

    private String resolveRequestId(
            HttpServletRequest request
    ) {
        Object requestId = request.getAttribute(
                RequestIdFilter.REQUEST_ID_ATTRIBUTE
        );

        if (requestId instanceof String value) {
            return value;
        }

        return "unknown";
    }
}