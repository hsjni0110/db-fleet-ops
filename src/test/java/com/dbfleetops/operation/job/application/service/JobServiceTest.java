package com.dbfleetops.operation.job.application.service;


import com.dbfleetops.operation.job.application.required.*;
import com.dbfleetops.operation.task.application.required.*;
import com.dbfleetops.operation.workflow.application.required.*;
import com.dbfleetops.operation.shared.application.required.*;
import com.dbfleetops.operation.job.domain.*;
import com.dbfleetops.operation.task.domain.*;
import com.dbfleetops.operation.workflow.domain.*;
import com.dbfleetops.operation.job.dto.*;
import com.dbfleetops.operation.task.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);
    private final DatabaseReader databases = mock(DatabaseReader.class);
    private final JobStore jobs = mock(JobStore.class);
    private final AuditWriter audit = mock(AuditWriter.class);
    private final ConfigurationChange configuration = mock(ConfigurationChange.class);
    private final JobPayloadFactory payloads = new JobPayloadFactory(new ObjectMapper());
    private final ConfigurationCommandFactory configurationCommands =
            new ConfigurationCommandFactory(new ObjectMapper());
    private final JobService service = new JobService(
            databases, jobs, audit, configuration, payloads, configurationCommands, CLOCK);

    @Test
    void createsBackupJobForActiveDatabase() {
        when(databases.findDatabase(1L)).thenReturn(Optional.of(new DatabaseExecutionTarget(
                1L, "orders", "mysql", 3306, "MYSQL", 2L, true)));
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.createBackupJob(1L, "key",
                new CreateBackupJobRequest("user", "full"));
        assertThat(response.jobType()).isEqualTo(JobType.BACKUP);
    }

    @Test
    void configurationApplyIsValidatedThroughPort() {
        when(databases.findDatabase(1L)).thenReturn(Optional.of(new DatabaseExecutionTarget(
                1L, "orders", "mysql", 3306, "MYSQL", 2L, true)));
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        var request = new CreateConfigurationApplyJobRequest(1L, "user", "reason",
                List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON")));
        service.createConfigurationApplyJob(1L, null, request);
        verify(configuration).validate(configurationCommands.change(1L, request));
    }

    @Test
    void returnsDuplicateConfigurationApplyWithoutValidatingOrSavingAgain() {
        var database = new DatabaseExecutionTarget(
                1L, "orders", "mysql", 3306, "MYSQL", 2L, true);
        var existingJob = OperationJob.create(
                JobType.CONFIGURATION_APPLY, 1L, "user", "same-key", "{}",
                java.time.LocalDateTime.now(CLOCK));
        var request = new CreateConfigurationApplyJobRequest(1L, "user", "reason",
                List.of(new ConfigurationApplyParameterRequest("slow_query_log", "ON")));

        when(databases.findDatabase(1L)).thenReturn(Optional.of(database));
        when(jobs.findDuplicate(1L, JobType.CONFIGURATION_APPLY, "same-key"))
                .thenReturn(Optional.of(existingJob));

        var response = service.createConfigurationApplyJob(1L, "same-key", request);

        assertThat(response.jobType()).isEqualTo(JobType.CONFIGURATION_APPLY);
        verify(configuration, never()).validate(any());
        verify(jobs, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsBackupWithoutRequester() {
        assertThatThrownBy(() -> service.createBackupJob(
                1L, null, new CreateBackupJobRequest("reason", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("요청자는 필수입니다.");

        verifyNoInteractions(databases, jobs, audit);
    }

    @Test
    void rejectsConfigurationApplyWithoutParameters() {
        var request = new CreateConfigurationApplyJobRequest(
                1L, "user", "reason", List.of());

        assertThatThrownBy(() -> service.createConfigurationApplyJob(1L, null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("적용할 설정 항목은 필수입니다.");

        verifyNoInteractions(databases, jobs, audit, configuration);
    }

    @Test
    void rejectsInactiveDatabase() {
        when(databases.findDatabase(1L)).thenReturn(Optional.of(new DatabaseExecutionTarget(
                1L, "orders", "mysql", 3306, "MYSQL", 2L, false)));

        assertThatThrownBy(() -> service.createBackupJob(
                1L, null, new CreateBackupJobRequest("reason", "user")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("비활성 관리 DB에는 Job을 생성할 수 없습니다.");

        verify(jobs, never()).save(any());
        verifyNoInteractions(audit);
    }
}
