package com.dbfleetops.operation.application;

import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.CreateConfigurationCheckJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import com.dbfleetops.operation.infra.OperationJobRepository;
import com.dbfleetops.policy.application.ConfigurationApplyValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationJobServiceConfigurationCheckTest {

    private final ManagedDatabaseRepository databaseRepository =
            mock(ManagedDatabaseRepository.class);

    private final OperationJobRepository jobRepository = mock(OperationJobRepository.class);

    private final AuditRecorderPort auditRecorderPort = mock(AuditRecorderPort.class);

    private final ConfigurationApplyValidationService configurationApplyValidationService =
            mock(ConfigurationApplyValidationService.class);

    private final OperationJobService service = new OperationJobService(databaseRepository,
            jobRepository, auditRecorderPort, configurationApplyValidationService);

    @Test
    void createConfigurationCheckJobCreatesQueuedJob() {
        ManagedDatabase database = mock(ManagedDatabase.class);

        when(database.getId()).thenReturn(1L);

        when(database.isActive()).thenReturn(true);

        when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));

        when(jobRepository.save(any(OperationJob.class))).thenAnswer(invocation -> {
            OperationJob job = invocation.getArgument(0);

            ReflectionTestUtils.setField(job, "id", 100L);

            return job;
        });

        OperationJobResponse response = service.createConfigurationCheckJob(1L, "config-check-1",
                new CreateConfigurationCheckJobRequest(10L, "local-user",
                        "daily configuration compliance check"));

        assertThat(response.jobId()).isEqualTo(100L);

        verify(jobRepository).save(argThat(job -> job.getJobType() == JobType.CONFIGURATION_CHECK
                && job.getTargetDatabaseId().equals(1L) && job.getRequestedBy().equals("local-user")
                && job.getIdempotencyKey().equals("config-check-1")
                && job.getRequestPayload().contains("\"profileId\":10")));

        verify(auditRecorderPort).record(eq("local-user"), eq("JOB_CREATED"), eq("OPERATION_JOB"),
                eq("100"), eq("SUCCESS"), contains("Configuration check job created"));
    }

    @Test
    void createConfigurationCheckJobReturnsExistingJobWhenIdempotencyKeyExists() {
        ManagedDatabase database = mock(ManagedDatabase.class);

        when(database.getId()).thenReturn(1L);

        when(database.isActive()).thenReturn(true);

        when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));

        OperationJob existingJob =
                OperationJob.create(JobType.CONFIGURATION_CHECK, 1L, "local-user", "config-check-1",
                        "{\"profileId\":10,\"reason\":\"daily\",\"requestedBy\":\"local-user\"}");

        ReflectionTestUtils.setField(existingJob, "id", 200L);

        when(jobRepository.findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(1L,
                JobType.CONFIGURATION_CHECK, "config-check-1"))
                        .thenReturn(Optional.of(existingJob));

        OperationJobResponse response = service.createConfigurationCheckJob(1L, "config-check-1",
                new CreateConfigurationCheckJobRequest(10L, "local-user",
                        "daily configuration compliance check"));

        assertThat(response.jobId()).isEqualTo(200L);

        verify(jobRepository, never()).save(any(OperationJob.class));
    }
}
