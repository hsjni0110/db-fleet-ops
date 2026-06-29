package com.dbfleetops.common.error;

public record ValidationErrorItem(
        String field,
        String message
) {
}