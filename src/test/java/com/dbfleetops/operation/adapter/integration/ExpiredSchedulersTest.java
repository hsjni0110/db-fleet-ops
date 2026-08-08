package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.operation.application.provided.ExpiredJobs;
import com.dbfleetops.operation.application.provided.ExpiredTasks;
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
