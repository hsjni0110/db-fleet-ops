package com.dbfleetops.health.dto;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.domain.SessionInfo;

public record SessionResponse(
        Long databaseId,
        DatabaseEngine engine,
        long processId,
        String user,
        String host,
        String databaseName,
        String command,
        long timeSeconds,
        String state,
        String queryPreview
) {
    public static SessionResponse from(
            Long databaseId,
            DatabaseEngine engine,
            SessionInfo info
    ) {
        return new SessionResponse(
                databaseId,
                engine,
                info.processId(),
                info.user(),
                info.host(),
                info.databaseName(),
                info.command(),
                info.timeSeconds(),
                info.state(),
                info.queryPreview()
        );
    }
}