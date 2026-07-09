package com.dbfleetops.operation.application;

import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import com.dbfleetops.operation.dto.FailJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import com.dbfleetops.operation.dto.SucceedJobRequest;
import com.dbfleetops.operation.infra.OperationJobRepository;
import com.dbfleetops.worker.application.WorkerShutdownState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationWorkerServiceTest {

        @Mock
        private OperationJobRepository jobRepository;

        @Mock
        private AuditRecorderPort auditRecorderPort;

        @Mock
        private OperationTaskService operationTaskService;

        @Mock
        private ConfigurationCheckJobExecutor configurationCheckJobExecutor;

        @Mock
        private WorkerShutdownState workerShutdownState;

        @Test
        void claimJobReturnsEmptyWhenNoQueuedJobExists() {
                when(jobRepository
                                .findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                                                eq(JobStatus.QUEUED), any(LocalDateTime.class)))
                                                                .thenReturn(List.of());

                OperationWorkerService service = new OperationWorkerService(jobRepository,
                                auditRecorderPort, operationTaskService,
                                configurationCheckJobExecutor, workerShutdownState);

                ClaimJobResponse response = service.claimJob("worker-1");

                assertThat(response.claimed()).isFalse();

                assertThat(response.jobId()).isNull();
        }

        @Test
        void claimJobChangesQueuedJobToRunningAndSetsLease() {
                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                when(jobRepository
                                .findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                                                eq(JobStatus.QUEUED), any(LocalDateTime.class)))
                                                                .thenReturn(List.of(job));

                OperationWorkerService service = new OperationWorkerService(jobRepository,
                                auditRecorderPort, operationTaskService,
                                configurationCheckJobExecutor, workerShutdownState);

                ClaimJobResponse response = service.claimJob("worker-1");

                assertThat(response.claimed()).isTrue();

                assertThat(response.jobType()).isEqualTo(JobType.BACKUP);

                assertThat(response.status()).isEqualTo(JobStatus.RUNNING);

                assertThat(response.targetDatabaseId()).isEqualTo(1L);

                assertThat(response.leaseOwner()).isEqualTo("worker-1");

                assertThat(response.leaseUntil()).isNotNull();

                assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);

                assertThat(job.getLeaseOwner()).isEqualTo("worker-1");

                assertThat(job.getLeaseUntil()).isNotNull();

                verify(auditRecorderPort).record(eq("worker-1"), eq("JOB_CLAIMED"),
                                eq("OPERATION_JOB"), any(), eq("SUCCESS"), contains("Job claimed"));
        }

        @Test
        void succeedJobChangesRunningJobToSucceeded() {
                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", LocalDateTime.now().plusSeconds(60));

                when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

                OperationWorkerService service = new OperationWorkerService(jobRepository,
                                auditRecorderPort, operationTaskService,
                                configurationCheckJobExecutor, workerShutdownState);

                OperationJobResponse response = service.succeedJob("worker-1", 1L,
                                new SucceedJobRequest("backup completed"));

                assertThat(response.status()).isEqualTo(JobStatus.SUCCEEDED);

                assertThat(response.resultCode()).isEqualTo("SUCCESS");

                assertThat(response.resultMessage()).isEqualTo("backup completed");

                assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);

                verify(auditRecorderPort).record(eq("worker-1"), eq("JOB_SUCCEEDED"),
                                eq("OPERATION_JOB"), any(), eq("SUCCESS"), eq("backup completed"));
        }

        @Test
        void failJobChangesRunningJobToFailed() {
                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", LocalDateTime.now().plusSeconds(60));

                when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

                OperationWorkerService service = new OperationWorkerService(jobRepository,
                                auditRecorderPort, operationTaskService,
                                configurationCheckJobExecutor, workerShutdownState);

                OperationJobResponse response = service.failJob("worker-1", 1L,
                                new FailJobRequest("BACKUP_FAILED", "mysqldump failed", false));

                assertThat(response.status()).isEqualTo(JobStatus.FAILED);

                assertThat(response.resultCode()).isEqualTo("BACKUP_FAILED");

                assertThat(response.resultMessage()).isEqualTo("mysqldump failed");

                assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);

                verify(auditRecorderPort).record(eq("worker-1"), eq("JOB_FAILED"),
                                eq("OPERATION_JOB"), any(), eq("FAILED"), eq("mysqldump failed"));
        }

        @Test
        void succeedJobThrowsExceptionWhenWorkerDoesNotOwnJob() {
                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", LocalDateTime.now().plusSeconds(60));

                when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

                OperationWorkerService service = new OperationWorkerService(jobRepository,
                                auditRecorderPort, operationTaskService,
                                configurationCheckJobExecutor, workerShutdownState);

                assertThrows(IllegalStateException.class, () -> service.succeedJob("worker-2", 1L,
                                new SucceedJobRequest("backup completed")));
        }

        @Test
        void failJobThrowsExceptionWhenJobIsNotRunning() {
                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

                OperationWorkerService service = new OperationWorkerService(jobRepository,
                                auditRecorderPort, operationTaskService,
                                configurationCheckJobExecutor, workerShutdownState);

                assertThrows(IllegalStateException.class, () -> service.failJob("worker-1", 1L,
                                new FailJobRequest("BACKUP_FAILED", "mysqldump failed", false)));
        }

        @Test
        void failJobKeepsFailedWhenRetryableIsFalse() {
                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", LocalDateTime.now().plusSeconds(60));

                when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

                OperationWorkerService service = new OperationWorkerService(jobRepository,
                                auditRecorderPort, operationTaskService,
                                configurationCheckJobExecutor, workerShutdownState);

                OperationJobResponse response = service.failJob("worker-1", 1L,
                                new FailJobRequest("BACKUP_FAILED", "mysqldump failed", false));

                assertThat(response.status()).isEqualTo(JobStatus.FAILED);

                assertThat(response.retryCount()).isZero();

                assertThat(response.resultCode()).isEqualTo("BACKUP_FAILED");
        }

        @Test
        void failJobRetriesWhenRetryableIsTrue() {
                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", LocalDateTime.now().plusSeconds(60));

                when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

                OperationWorkerService service = new OperationWorkerService(jobRepository,
                                auditRecorderPort, operationTaskService,
                                configurationCheckJobExecutor, workerShutdownState);

                OperationJobResponse response = service.failJob("worker-1", 1L,
                                new FailJobRequest("BACKUP_FAILED", "temporary error", true));

                assertThat(response.status()).isEqualTo(JobStatus.QUEUED);

                assertThat(response.retryCount()).isEqualTo(1);

                assertThat(response.availableAt()).isNotNull();

                assertThat(response.leaseOwner()).isNull();

                assertThat(response.leaseUntil()).isNull();

                verify(auditRecorderPort).record(eq("worker-1"), eq("JOB_FAILED"),
                                eq("OPERATION_JOB"), any(), eq("FAILED"), eq("temporary error"));

                verify(auditRecorderPort).record(eq("worker-1"), eq("JOB_RETRIED"),
                                eq("OPERATION_JOB"), any(), eq("SUCCESS"),
                                contains("retryCount=1"));
        }

        @Test
        void claimBackupJobCreatesOperationTask() {
                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                when(jobRepository
                                .findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                                                eq(JobStatus.QUEUED), any(LocalDateTime.class)))
                                                                .thenReturn(List.of(job));

                OperationWorkerService service = new OperationWorkerService(jobRepository,
                                auditRecorderPort, operationTaskService,
                                configurationCheckJobExecutor, workerShutdownState);

                ClaimJobResponse response = service.claimJob("worker-1");

                assertThat(response.claimed()).isTrue();

                assertThat(response.status()).isEqualTo(JobStatus.RUNNING);

                verify(operationTaskService).createBackupTaskForOperationJob(nullable(Long.class),
                                eq(1L));

                verify(auditRecorderPort).record(eq("worker-1"), eq("OPERATION_TASK_CREATED"),
                                eq("OPERATION_JOB"), nullable(String.class), eq("SUCCESS"),
                                contains("Backup operation task created"));
        }
}
