package com.dbfleetops.operation.task.application.service;


import com.dbfleetops.operation.shared.application.required.AgentReader;
import com.dbfleetops.operation.shared.application.required.AgentExecutionTarget;
import com.dbfleetops.operation.shared.application.required.CredentialReference;
import com.dbfleetops.operation.shared.application.required.DatabaseExecutionTarget;
import com.dbfleetops.operation.shared.application.required.ResolvedTaskCredential;
import com.dbfleetops.operation.shared.application.required.CredentialReader;
import com.dbfleetops.operation.shared.application.required.DatabaseReader;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import com.dbfleetops.operation.task.dto.ResolveTaskCredentialRequest;
import com.dbfleetops.operation.task.domain.TaskExecutionConflictException;
import com.dbfleetops.operation.task.application.required.TaskStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskCredentialServiceTest {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);
    @Mock AgentReader agents;
    @Mock TaskStore tasks;
    @Mock CredentialReader credentials;
    @Mock DatabaseReader databases;

    @Test
    void returnsCredentialOnlyForTheCurrentExecution() {
        OperationTask task = OperationTask.createForJob(1L, 10L, 7L,
                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");
        task.claim(LocalDateTime.now(CLOCK), LocalDateTime.now(CLOCK).plusSeconds(60));
        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(agents.matchesToken(1L, "token")).thenReturn(true);
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
        when(credentials.findCredential(7L)).thenReturn(Optional.of(new CredentialReference(7L, 3L)));
        when(databases.findDatabase(3L)).thenReturn(Optional.of(new DatabaseExecutionTarget(
                3L, "orders", "mysql", 3306, "MYSQL", 1L, true)));
        when(credentials.resolve(7L)).thenReturn(new ResolvedTaskCredential(3L, "backup", "secret"));
        TaskCredentialService service = new TaskCredentialService(agents, tasks, credentials,
                databases, CLOCK);

        var response = service.resolve(1L, 20L,
                new ResolveTaskCredentialRequest("token", 1));

        assertThat(response.username()).isEqualTo("backup");
        assertThat(response.password()).isEqualTo("secret");
        assertThatThrownBy(() -> service.resolve(1L, 20L,
                new ResolveTaskCredentialRequest("token", 2)))
                .isInstanceOf(TaskExecutionConflictException.class);
    }

    @Test
    void rejectsCredentialWhenDatabaseIsAssignedToAnotherAgent() {
        OperationTask task = OperationTask.createForJob(1L, 10L, 7L,
                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");
        task.claim(LocalDateTime.now(CLOCK), LocalDateTime.now(CLOCK).plusSeconds(60));
        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(agents.matchesToken(1L, "token")).thenReturn(true);
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
        when(credentials.findCredential(7L))
                .thenReturn(Optional.of(new CredentialReference(7L, 3L)));
        when(databases.findDatabase(3L)).thenReturn(Optional.of(new DatabaseExecutionTarget(
                3L, "orders", "mysql", 3306, "MYSQL", 2L, true)));
        TaskCredentialService service = new TaskCredentialService(agents, tasks, credentials,
                databases, CLOCK);

        assertThatThrownBy(() -> service.resolve(1L, 20L,
                new ResolveTaskCredentialRequest("token", 1)))
                .isInstanceOf(TaskExecutionConflictException.class)
                .hasMessageContaining("다른 Agent");
    }

}
