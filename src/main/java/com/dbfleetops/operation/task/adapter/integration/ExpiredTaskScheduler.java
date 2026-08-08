package com.dbfleetops.operation.task.adapter.integration;

import com.dbfleetops.operation.task.application.provided.ExpiredTasks;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "db-fleetops.task-lease", name = "reaper-enabled",
        havingValue = "true", matchIfMissing = true)
public class ExpiredTaskScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExpiredTaskScheduler.class);
    private final ExpiredTasks expiredTasks;
    public ExpiredTaskScheduler(ExpiredTasks expiredTasks) { this.expiredTasks = expiredTasks; }
    @Scheduled(fixedDelayString = "${db-fleetops.task-lease.expiration-check-interval:5s}")
    public void recoverExpiredTasks() {
        try {
            int count = expiredTasks.recoverExpiredTasks();
            if (count > 0) log.info("expired_operation_tasks_recovered count={}", count);
        } catch (Exception exception) {
            log.error("expired_operation_task_recovery_failed", exception);
        }
    }
}
