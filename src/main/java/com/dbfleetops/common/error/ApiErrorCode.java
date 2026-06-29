package com.dbfleetops.common.error;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {

    INTERNAL_SERVER_ERROR(
            "DBOPS-COMMON-50001",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            "An unexpected server error occurred."
    ),

    INVALID_REQUEST(
            "DBOPS-COMMON-40001",
            HttpStatus.BAD_REQUEST,
            "Invalid request",
            "The request is invalid."
    ),

    RESOURCE_NOT_FOUND(
            "DBOPS-COMMON-40401",
            HttpStatus.NOT_FOUND,
            "Resource not found",
            "The requested resource could not be found."
    ),

    METHOD_NOT_ALLOWED(
            "DBOPS-COMMON-40501",
            HttpStatus.METHOD_NOT_ALLOWED,
            "Method not allowed",
            "The HTTP method is not supported for this resource."
    );

    private final String code;
    private final HttpStatus status;
    private final String title;
    private final String detail;

    ApiErrorCode(
            String code,
            HttpStatus status,
            String title,
            String detail
    ) {
        this.code = code;
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }
}