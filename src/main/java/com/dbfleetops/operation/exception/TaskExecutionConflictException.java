package com.dbfleetops.operation.exception;

public class TaskExecutionConflictException extends RuntimeException {
    public TaskExecutionConflictException(String message) {
        super(message);
    }

    public TaskExecutionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
