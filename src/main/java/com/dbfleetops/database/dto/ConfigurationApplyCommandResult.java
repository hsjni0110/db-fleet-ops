package com.dbfleetops.database.dto;

public record ConfigurationApplyCommandResult(String parameterName, String requestedValue,
        boolean success, String message) {

    public static ConfigurationApplyCommandResult success(String parameterName,
            String requestedValue, String message) {
        return new ConfigurationApplyCommandResult(parameterName, requestedValue, true, message);
    }

    public static ConfigurationApplyCommandResult failure(String parameterName,
            String requestedValue, String message) {
        return new ConfigurationApplyCommandResult(parameterName, requestedValue, false, message);
    }
}
