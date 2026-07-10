package com.dbfleetops.worker.application;

import com.dbfleetops.operation.application.OperationWorkerService;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationJobClaimSchedulerTest {

    @Test
    void claimAvailableJobCallsWorkerServiceWithConfiguredWorkerId() {
        OperationWorkerService workerService = mock(OperationWorkerService.class);
        WorkerShutdownState workerShutdownState = mock(WorkerShutdownState.class);
        WorkerProperties workerProperties = new WorkerProperties();

        workerProperties.setId("worker-test-1");

        when(workerShutdownState.isShuttingDown()).thenReturn(false);
        when(workerService.claimJob("worker-test-1"))
                .thenReturn(new ClaimJobResponse(
                        true,
                        1L,
                        JobType.BACKUP,
                        JobStatus.RUNNING,
                        1L,
                        "worker-test-1",
                        LocalDateTime.now().plusSeconds(60)
                ));

        OperationJobClaimScheduler scheduler =
                new OperationJobClaimScheduler(
                        workerService,
                        workerShutdownState,
                        workerProperties
                );

        scheduler.claimAvailableJob();

        verify(workerService).claimJob("worker-test-1");
    }

    @Test
    void claimAvailableJobSkipsClaimWhenWorkerIsShuttingDown() {
        OperationWorkerService workerService = mock(OperationWorkerService.class);
        WorkerShutdownState workerShutdownState = mock(WorkerShutdownState.class);
        WorkerProperties workerProperties = new WorkerProperties();

        workerProperties.setId("worker-test-1");

        when(workerShutdownState.isShuttingDown()).thenReturn(true);

        OperationJobClaimScheduler scheduler =
                new OperationJobClaimScheduler(
                        workerService,
                        workerShutdownState,
                        workerProperties
                );

        scheduler.claimAvailableJob();

        verify(workerService, never()).claimJob("worker-test-1");
    }
}
