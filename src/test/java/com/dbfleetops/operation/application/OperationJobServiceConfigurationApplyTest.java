package com.dbfleetops.operation.application;

import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ConfigurationApplyParameterRequest;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import com.dbfleetops.operation.infra.OperationJobRepository;
import com.dbfleetops.policy.application.ConfigurationApplyValidationService;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OperationJobServiceConfigurationApplyTest {

    private final ManagedDatabaseRepository databaseRepository =
            mock(ManagedDatabaseRepository.class);

    private final OperationJobRepository jobRepository = mock(OperationJobRepository.class);

    private final AuditRecorderPort auditRecorderPort = mock(AuditRecorderPort.class);

    private final ConfigurationApplyValidationService applyValidationService =
            mock(ConfigurationApplyValidationService.class);

    private final OperationJobService service = new OperationJobService(databaseRepository,
            jobRepository, auditRecorderPort, applyValidationService);

    @Test
    void createConfigurationApplyJobCreatesQueuedJob() {
        ManagedDatabase database = mock(ManagedDatabase.class);

        when(database.getId()).thenReturn(1L);

        when(database.isActive()).thenReturn(true);

        when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));

        when(applyValidationService.validate(eq(1L), any(CreateConfigurationApplyJobRequest.class)))
                .thenReturn(new ConfigurationApplyValidationResult(1L, 1L, List.of()));

        when(jobRepository.save(any(OperationJob.class))).thenAnswer(invocation -> {
            OperationJob job = invocation.getArgument(0);

            ReflectionTestUtils.setField(job, "id", 100L);

            return job;
        });

        OperationJobResponse response = service.createConfigurationApplyJob(1L,
                "idem-config-apply-001",
                new CreateConfigurationApplyJobRequest(1L, "local-user", "enable slow query log",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON"),
                                new ConfigurationApplyParameterRequest("long_query_time", "1.0"))));

        assertThat(response.jobId()).isEqualTo(100L);

        verify(applyValidationService).validate(eq(1L),
                any(CreateConfigurationApplyJobRequest.class));

        verify(jobRepository).save(argThat(job -> job.getJobType() == JobType.CONFIGURATION_APPLY
                && job.getTargetDatabaseId().equals(1L) && job.getRequestedBy().equals("local-user")
                && job.getIdempotencyKey().equals("idem-config-apply-001")
                && job.getRequestPayload().contains("\"profileId\":1")
                && job.getRequestPayload().contains("\"parameters\"")
                && job.getRequestPayload().contains("\"slow_query_log\"")
                && job.getRequestPayload().contains("\"long_query_time\"")));

        verify(auditRecorderPort).record(eq("local-user"), eq("JOB_CREATED"), eq("OPERATION_JOB"),
                eq("100"), eq("SUCCESS"), contains("Configuration apply job created"));
    }

    @Test
    void createConfigurationApplyJobReturnsExistingJobWhenIdempotencyKeyExists() {
        ManagedDatabase database = mock(ManagedDatabase.class);

        when(database.getId()).thenReturn(1L);

        when(database.isActive()).thenReturn(true);

        when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));

        OperationJob existingJob = OperationJob.create(JobType.CONFIGURATION_APPLY, 1L,
                "local-user", "idem-config-apply-001",
                """
                        {"profileId":1,"reason":"enable slow query log","requestedBy":"local-user","parameters":[{"parameterName":"slow_query_log","targetValue":"ON"}]}
                        """
                        .trim());

        ReflectionTestUtils.setField(existingJob, "id", 200L);

        when(jobRepository.findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(1L,
                JobType.CONFIGURATION_APPLY, "idem-config-apply-001"))
                        .thenReturn(Optional.of(existingJob));

        OperationJobResponse response = service.createConfigurationApplyJob(1L,
                "idem-config-apply-001",
                new CreateConfigurationApplyJobRequest(1L, "local-user", "enable slow query log",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON"))));

        assertThat(response.jobId()).isEqualTo(200L);

        verify(applyValidationService, never()).validate(anyLong(),
                any(CreateConfigurationApplyJobRequest.class));

        verify(jobRepository, never()).save(any(OperationJob.class));
    }

    @Test
    void createConfigurationApplyJobThrowsExceptionWhenProfileIdIsMissing() {
        assertThatThrownBy(() -> service.createConfigurationApplyJob(1L, "idem-config-apply-001",
                new CreateConfigurationApplyJobRequest(null, "local-user", "enable slow query log",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON")))))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("profileId is required");
    }

    @Test
    void createConfigurationApplyJobThrowsExceptionWhenRequestedByIsMissing() {
        assertThatThrownBy(() -> service.createConfigurationApplyJob(1L, "idem-config-apply-001",
                new CreateConfigurationApplyJobRequest(1L, "", "enable slow query log",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON")))))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("requestedBy is required");
    }

    @Test
    void createConfigurationApplyJobThrowsExceptionWhenParametersAreEmpty() {
        assertThatThrownBy(() -> service.createConfigurationApplyJob(1L, "idem-config-apply-001",
                new CreateConfigurationApplyJobRequest(1L, "local-user", "enable slow query log",
                        List.of()))).isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("parameters is required");
    }

    @Test
    void createConfigurationApplyJobThrowsExceptionWhenParameterNameIsMissing() {
        assertThatThrownBy(() -> service.createConfigurationApplyJob(1L, "idem-config-apply-001",
                new CreateConfigurationApplyJobRequest(1L, "local-user", "enable slow query log",
                        List.of(new ConfigurationApplyParameterRequest("", "ON")))))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("parameterName is required");
    }

    @Test
    void createConfigurationApplyJobThrowsExceptionWhenTargetValueIsMissing() {
        assertThatThrownBy(() -> service.createConfigurationApplyJob(1L, "idem-config-apply-001",
                new CreateConfigurationApplyJobRequest(1L, "local-user", "enable slow query log",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "")))))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("targetValue is required");
    }

    @Test
    void createConfigurationApplyJobThrowsExceptionWhenDatabaseIsInactive() {
        ManagedDatabase database = mock(ManagedDatabase.class);

        when(database.getId()).thenReturn(1L);

        when(database.isActive()).thenReturn(false);

        when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));

        assertThatThrownBy(() -> service.createConfigurationApplyJob(1L, "idem-config-apply-001",
                new CreateConfigurationApplyJobRequest(1L, "local-user", "enable slow query log",
                        List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON")))))
                                .isInstanceOf(IllegalStateException.class).hasMessageContaining(
                                        "Inactive database cannot create operation job");
    }
}
