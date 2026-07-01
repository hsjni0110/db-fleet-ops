package com.dbfleetops.health.domain;

public record SessionInfo(
        long processId,
        String user,
        String host,
        String databaseName,
        String command,
        long timeSeconds,
        String state,
        String queryPreview
) {
}