package com.dbfleetops.health.domain;

import lombok.Getter;
import lombok.AccessLevel;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@Entity
public class DatabaseHealthResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter(AccessLevel.NONE)
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

}
