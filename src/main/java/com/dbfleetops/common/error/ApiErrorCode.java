package com.dbfleetops.common.error;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {

    INVALID_REQUEST("DBOPS-COMMON-40001", HttpStatus.BAD_REQUEST, "Invalid request",
            "The request is invalid.", "invalid-request"),

    VALIDATION_FAILED("DBOPS-COMMON-40002", HttpStatus.BAD_REQUEST, "Validation failed",
            "One or more request fields are invalid.", "validation-failed"),

    RESOURCE_NOT_FOUND("DBOPS-COMMON-40401", HttpStatus.NOT_FOUND, "Resource not found",
            "The requested resource could not be found.", "resource-not-found"),

    METHOD_NOT_ALLOWED("DBOPS-COMMON-40501", HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
            "The HTTP method is not supported for this resource.", "method-not-allowed"),

    INTERNAL_SERVER_ERROR("DBOPS-COMMON-50001", HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error", "An unexpected server error occurred.",
            "internal-server-error"),

    DATABASE_NOT_REACHABLE("DBOPS-DATABASE-40001", HttpStatus.BAD_REQUEST, "Database not reachable",
            "The database does not exist or cannot be reached with the provided connection information.",
            "database-not-reachable");

    private final String code;
    private final HttpStatus status;
    private final String title;
    private final String detail;
    private final String typePath;

    ApiErrorCode(String code, HttpStatus status, String title, String detail, String typePath) {
        this.code = code;
        this.status = status;
        this.title = title;
        this.detail = detail;
        this.typePath = typePath;
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

    public String typePath() {
        return typePath;
    }
}
