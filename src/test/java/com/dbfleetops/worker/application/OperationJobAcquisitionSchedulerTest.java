package com.dbfleetops.worker.application;

import com.dbfleetops.operation.job.application.provided.JobAcquisition;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.dto.JobAcquisitionResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationJobAcquisitionSchedulerTest {

    @Test
    void acquireAvailableJobRequestsAJobWithConfiguredWorkerId() {
        JobAcquisition workerService = mock(JobAcquisition.class);
        WorkerShutdownState workerShutdownState = mock(WorkerShutdownState.class);
        WorkerProperties workerProperties = new WorkerProperties();

        workerProperties.setId("worker-test-1");

        when(workerShutdownState.isShuttingDown()).thenReturn(false);
        when(workerService.acquireJob("worker-test-1"))
                .thenReturn(new JobAcquisitionResponse(
                        true,
                        1L,
                        JobType.BACKUP,
                        JobStatus.RUNNING,
                        1L,
                        "worker-test-1",
                        LocalDateTime.now().plusSeconds(60)
                ));

        OperationJobAcquisitionScheduler scheduler =
                new OperationJobAcquisitionScheduler(
                        workerService,
                        workerShutdownState,
                        workerProperties
                );

        scheduler.acquireAvailableJob();

        verify(workerService).acquireJob("worker-test-1");
    }

    @Test
    void acquireAvailableJobSkipsAcquisitionWhenWorkerIsShuttingDown() {
        JobAcquisition workerService = mock(JobAcquisition.class);
        WorkerShutdownState workerShutdownState = mock(WorkerShutdownState.class);
        WorkerProperties workerProperties = new WorkerProperties();

        workerProperties.setId("worker-test-1");

        when(workerShutdownState.isShuttingDown()).thenReturn(true);

        OperationJobAcquisitionScheduler scheduler =
                new OperationJobAcquisitionScheduler(
                        workerService,
                        workerShutdownState,
                        workerProperties
                );

        scheduler.acquireAvailableJob();

        verify(workerService, never()).acquireJob("worker-test-1");
    }
}
