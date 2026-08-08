package com.dbfleetops.operation.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "db-fleetops.job-lease", name = "reaper-enabled",
        havingValue = "true", matchIfMissing = true)
public class ExpiredOperationJobScheduler {
    private final ExpiredOperationJobService service;

    public ExpiredOperationJobScheduler(ExpiredOperationJobService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${db-fleetops.job-lease.expiration-check-interval:5s}")
    public void recoverExpiredJobs() {
        service.recoverExpiredJobs();
    }
}
