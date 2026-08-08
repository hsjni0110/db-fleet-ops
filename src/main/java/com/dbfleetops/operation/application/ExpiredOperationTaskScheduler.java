package com.dbfleetops.operation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "db-fleetops.task-lease", name = "reaper-enabled",
        havingValue = "true", matchIfMissing = true)
public class ExpiredOperationTaskScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExpiredOperationTaskScheduler.class);
    private final ExpiredOperationTaskService service;

    public ExpiredOperationTaskScheduler(ExpiredOperationTaskService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${db-fleetops.task-lease.expiration-check-interval:5s}")
    public void recoverExpiredTasks() {
        try {
            int count = service.recoverExpiredTasks();
            if (count > 0) log.info("expired_operation_tasks_recovered count={}", count);
        } catch (Exception exception) {
            log.error("expired_operation_task_recovery_failed", exception);
        }
    }
}
