package com.dbfleetops.operation.workflow.adapter.scheduler;


import com.dbfleetops.operation.task.application.provided.ExpiredTasks;
import com.dbfleetops.operation.task.adapter.integration.ExpiredTaskScheduler;
import com.dbfleetops.operation.workflow.application.provided.ExpiredJobs;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ExpiredSchedulersTest {
    @Test
    void jobSchedulerCallsProvidedPort() {
        ExpiredJobs expiredJobs = mock(ExpiredJobs.class);

        new ExpiredJobScheduler(expiredJobs).recoverExpiredJobs();

        verify(expiredJobs).recoverExpiredJobs();
    }

    @Test
    void taskSchedulerCallsProvidedPort() {
        ExpiredTasks expiredTasks = mock(ExpiredTasks.class);

        new ExpiredTaskScheduler(expiredTasks).recoverExpiredTasks();

        verify(expiredTasks).recoverExpiredTasks();
    }
}
