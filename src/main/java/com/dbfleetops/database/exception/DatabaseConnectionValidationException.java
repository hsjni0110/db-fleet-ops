package com.dbfleetops.database.exception;

public class DatabaseConnectionValidationException extends RuntimeException {

    public DatabaseConnectionValidationException(String message) {
        super(message);
    }

    public DatabaseConnectionValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
