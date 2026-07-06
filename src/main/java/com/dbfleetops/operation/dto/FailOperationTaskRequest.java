package com.dbfleetops.operation.dto;

public record FailOperationTaskRequest(String agentToken, String errorCode, String errorMessage) {
}
