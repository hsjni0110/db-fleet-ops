package com.dbfleetops.operation.dto;

public record FailJobRequest(
        String resultCode,
        String resultMessage
) {
}