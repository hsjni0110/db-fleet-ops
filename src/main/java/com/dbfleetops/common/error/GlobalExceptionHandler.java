package com.dbfleetops.common.error;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.dbfleetops.common.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler
        extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GlobalExceptionHandler.class
            );

    private static final ZoneId DEFAULT_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private static final String PROBLEM_TYPE_BASE_URI =
            "https://db-fleetops.dev/problems/";

    /*
     * @Valid가 적용된 Request Body 검증 실패
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ApiErrorCode errorCode =
                ApiErrorCode.VALIDATION_FAILED;

        List<ValidationErrorItem> errors =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(fieldError ->
                                new ValidationErrorItem(
                                        fieldError.getField(),
                                        resolveValidationMessage(
                                                fieldError.getDefaultMessage()
                                        )
                                )
                        )
                        .toList();

        ProblemDetail problem = createProblemDetail(
                errorCode,
                request
        );

        problem.setProperty("errors", errors);

        log.warn(
                "request_validation_failed"
                        + " requestId={}"
                        + " method={}"
                        + " path={}"
                        + " errorCount={}",
                resolveRequestId(request),
                resolveMethod(request),
                resolvePath(request),
                errors.size()
        );

        return handleExceptionInternal(
                exception,
                problem,
                headers,
                errorCode.status(),
                request
        );
    }

    /*
     * 존재하는 URL에 지원하지 않는 HTTP Method 사용
     */
    @Override
    protected ResponseEntity<Object>
            handleHttpRequestMethodNotSupported(
                    HttpRequestMethodNotSupportedException exception,
                    HttpHeaders headers,
                    HttpStatusCode status,
                    WebRequest request
            ) {
        ApiErrorCode errorCode =
                ApiErrorCode.METHOD_NOT_ALLOWED;

        ProblemDetail problem = createProblemDetail(
                errorCode,
                request
        );

        if (exception.getSupportedHttpMethods() != null) {
            problem.setProperty(
                    "allowedMethods",
                    exception.getSupportedHttpMethods()
                        .stream()
                        .map(httpMethod -> httpMethod.name())
                        .sorted()
                        .toList()
                            );
        }

        log.warn(
                "http_method_not_allowed"
                        + " requestId={}"
                        + " method={}"
                        + " path={}",
                resolveRequestId(request),
                resolveMethod(request),
                resolvePath(request)
        );

        return handleExceptionInternal(
                exception,
                problem,
                headers,
                errorCode.status(),
                request
        );
    }

    /*
     * 정적 Resource Handler까지 확인했지만 Resource를 찾지 못한 경우
     */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return createNotFoundResponse(
                exception,
                headers,
                request
        );
    }

    /*
     * DispatcherServlet이 Handler 자체를 찾지 못한 경우
     */
    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return createNotFoundResponse(
                exception,
                headers,
                request
        );
    }

    /*
     * 위에서 분류하지 못한 예상 밖의 오류
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        ApiErrorCode errorCode =
                ApiErrorCode.INTERNAL_SERVER_ERROR;

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

        ProblemDetail problem = createProblemDetail(
                errorCode,
                request
        );

        return ResponseEntity
                .status(errorCode.status())
                .body(problem);
    }

    private ResponseEntity<Object> createNotFoundResponse(
            Exception exception,
            HttpHeaders headers,
            WebRequest request
    ) {
        ApiErrorCode errorCode =
                ApiErrorCode.RESOURCE_NOT_FOUND;

        ProblemDetail problem = createProblemDetail(
                errorCode,
                request
        );

        log.warn(
                "resource_not_found"
                        + " requestId={}"
                        + " method={}"
                        + " path={}",
                resolveRequestId(request),
                resolveMethod(request),
                resolvePath(request)
        );

        return handleExceptionInternal(
                exception,
                problem,
                headers,
                errorCode.status(),
                request
        );
    }

    private ProblemDetail createProblemDetail(
            ApiErrorCode errorCode,
            WebRequest request
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        errorCode.status(),
                        errorCode.detail()
                );

        problem.setType(
                URI.create(
                        PROBLEM_TYPE_BASE_URI
                                + errorCode.typePath()
                )
        );

        problem.setTitle(errorCode.title());
        problem.setInstance(
                URI.create(resolvePath(request))
        );

        problem.setProperty(
                "errorCode",
                errorCode.code()
        );

        problem.setProperty(
                "requestId",
                resolveRequestId(request)
        );

        problem.setProperty(
                "timestamp",
                OffsetDateTime.now(DEFAULT_ZONE_ID)
        );

        return problem;
    }

    private ProblemDetail createProblemDetail(
            ApiErrorCode errorCode,
            HttpServletRequest request
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        errorCode.status(),
                        errorCode.detail()
                );

        problem.setType(
                URI.create(
                        PROBLEM_TYPE_BASE_URI
                                + errorCode.typePath()
                )
        );

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
                resolveRequestId(request)
        );

        problem.setProperty(
                "timestamp",
                OffsetDateTime.now(DEFAULT_ZONE_ID)
        );

        return problem;
    }

    private String resolveRequestId(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return resolveRequestId(
                    servletWebRequest.getRequest()
            );
        }

        return "unknown";
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

    private String resolvePath(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest
                    .getRequest()
                    .getRequestURI();
        }

        return "/";
    }

    private String resolveMethod(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest
                    .getRequest()
                    .getMethod();
        }

        return "UNKNOWN";
    }

    private String resolveValidationMessage(
            String defaultMessage
    ) {
        if (
                defaultMessage == null
                || defaultMessage.isBlank()
        ) {
            return "Invalid value.";
        }

        return defaultMessage;
    }
}