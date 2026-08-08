package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.operation.application.provided.ExpiredJobs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "db-fleetops.job-lease", name = "reaper-enabled",
        havingValue = "true", matchIfMissing = true)
public class ExpiredJobScheduler {
    private final ExpiredJobs expiredJobs;
    public ExpiredJobScheduler(ExpiredJobs expiredJobs) { this.expiredJobs = expiredJobs; }
    @Scheduled(fixedDelayString = "${db-fleetops.job-lease.expiration-check-interval:5s}")
    public void recoverExpiredJobs() { expiredJobs.recoverExpiredJobs(); }
}
