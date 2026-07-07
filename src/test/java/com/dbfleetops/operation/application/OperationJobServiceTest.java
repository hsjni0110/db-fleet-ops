package com.dbfleetops.operation.application;

import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.CreateBackupJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import com.dbfleetops.operation.infra.OperationJobRepository;
import com.dbfleetops.policy.application.ConfigurationApplyValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationJobServiceTest {

        @Mock
        private ManagedDatabaseRepository databaseRepository;

        @Mock
        private OperationJobRepository jobRepository;

        @Mock
        private AuditRecorderPort auditRecorderPort;

        @Mock
        private ConfigurationApplyValidationService configurationApplyValidationService;

        @Test
        void createBackupJobCreatesQueuedJob() {
                ManagedDatabase database = newDatabase();

                CreateBackupJobRequest request =
                                new CreateBackupJobRequest("manual backup test", "local-user");

                OperationJob savedJob = OperationJob.create(JobType.BACKUP, 1L, "local-user",
                                "idem-001", "{\"reason\":\"manual backup test\"}");

                when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));

                when(jobRepository.findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(1L,
                                JobType.BACKUP, "idem-001")).thenReturn(Optional.empty());

                when(jobRepository.save(any(OperationJob.class))).thenReturn(savedJob);

                OperationJobService service = new OperationJobService(databaseRepository,
                                jobRepository, auditRecorderPort,
                                configurationApplyValidationService);

                OperationJobResponse response = service.createBackupJob(1L, "idem-001", request);

                assertThat(response.jobType()).isEqualTo(JobType.BACKUP);

                assertThat(response.status()).isEqualTo(JobStatus.QUEUED);

                assertThat(response.targetDatabaseId()).isEqualTo(1L);

                assertThat(response.requestedBy()).isEqualTo("local-user");

                verify(jobRepository).save(any(OperationJob.class));

                verify(auditRecorderPort).record(eq("local-user"), eq("JOB_CREATED"),
                                eq("OPERATION_JOB"), any(), eq("SUCCESS"),
                                contains("Backup job created"));
        }

        @Test
        void createBackupJobReturnsExistingJobWhenIdempotencyKeyAlreadyExists() {
                ManagedDatabase database = newDatabase();

                OperationJob existingJob = OperationJob.create(JobType.BACKUP, 1L, "local-user",
                                "idem-001", "{\"reason\":\"manual backup test\"}");

                CreateBackupJobRequest request =
                                new CreateBackupJobRequest("manual backup test", "local-user");

                when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));

                when(jobRepository.findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(1L,
                                JobType.BACKUP, "idem-001")).thenReturn(Optional.of(existingJob));

                OperationJobService service = new OperationJobService(databaseRepository,
                                jobRepository, auditRecorderPort,
                                configurationApplyValidationService);

                OperationJobResponse response = service.createBackupJob(1L, "idem-001", request);

                assertThat(response.jobType()).isEqualTo(JobType.BACKUP);

                assertThat(response.status()).isEqualTo(JobStatus.QUEUED);

                verify(jobRepository, never()).save(any(OperationJob.class));

                verify(auditRecorderPort, never()).record(any(), any(), any(), any(), any(), any());
        }

        @Test
        void createBackupJobThrowsExceptionWhenDatabaseIsInactive() {
                ManagedDatabase database = newDatabase();

                database.deactivate();

                when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));

                OperationJobService service = new OperationJobService(databaseRepository,
                                jobRepository, auditRecorderPort,
                                configurationApplyValidationService);

                assertThrows(IllegalStateException.class, () -> service.createBackupJob(1L,
                                "idem-001",
                                new CreateBackupJobRequest("manual backup test", "local-user")));

                verifyNoInteractions(jobRepository);
        }

        @Test
        void getJobReturnsJobResponse() {
                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

                OperationJobService service = new OperationJobService(databaseRepository,
                                jobRepository, auditRecorderPort,
                                configurationApplyValidationService);

                OperationJobResponse response = service.getJob(10L);

                assertThat(response.jobType()).isEqualTo(JobType.BACKUP);

                assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
        }

        private ManagedDatabase newDatabase() {
                return new ManagedDatabase("order-mysql", "localhost", 3306, "orders",
                                DatabaseEngine.MYSQL, "LOCAL", "order-service", "platform-team",
                                "test database");
        }
}
