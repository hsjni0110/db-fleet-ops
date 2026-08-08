package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.*;
import com.dbfleetops.operation.dto.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobServiceTest {
    private final DatabaseReader databases = mock(DatabaseReader.class);
    private final JobStore jobs = mock(JobStore.class);
    private final AuditWriter audit = mock(AuditWriter.class);
    private final ConfigurationJobRunner configuration = mock(ConfigurationJobRunner.class);
    private final JobService service = new JobService(databases, jobs, audit, configuration);

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
        verify(configuration).validateApply(1L, request);
    }
}
