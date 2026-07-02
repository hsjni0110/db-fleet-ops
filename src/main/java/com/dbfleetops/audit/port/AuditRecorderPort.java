package com.dbfleetops.audit.port;

public interface AuditRecorderPort {

    void record(
            String actor,
            String action,
            String resourceType,
            String resourceId,
            String result,
            String message
    );
}