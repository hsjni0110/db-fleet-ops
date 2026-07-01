package com.dbfleetops.health.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class DatabaseHealthResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long databaseId;

    @Enumerated(EnumType.STRING)
    private HealthStatus status;

    private boolean connectionSuccess;

    private long responseTimeMs;

    private String message;

    private LocalDateTime checkedAt;

    protected DatabaseHealthResult() {
    }

    public DatabaseHealthResult(
            Long databaseId,
            HealthStatus status,
            boolean connectionSuccess,
            long responseTimeMs,
            String message
    ) {
        this.databaseId = databaseId;
        this.status = status;
        this.connectionSuccess = connectionSuccess;
        this.responseTimeMs = responseTimeMs;
        this.message = message;
        this.checkedAt = LocalDateTime.now();
    }

    public Long getDatabaseId() { return databaseId; }
    public HealthStatus getStatus() { return status; }
    public boolean isConnectionSuccess() { return connectionSuccess; }
    public long getResponseTimeMs() { return responseTimeMs; }
    public String getMessage() { return message; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
}