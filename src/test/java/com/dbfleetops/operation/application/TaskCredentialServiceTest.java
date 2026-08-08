package com.dbfleetops.operation.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.database.application.CredentialCipher;
import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.dto.RegisterManagedDatabaseRequest;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.dto.ResolveTaskCredentialRequest;
import com.dbfleetops.operation.exception.TaskExecutionConflictException;
import com.dbfleetops.operation.infra.OperationTaskRepository;
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
    @Mock AgentRepository agents;
    @Mock OperationTaskRepository tasks;
    @Mock DatabaseCredentialRepository credentials;
    @Mock ManagedDatabaseRepository databases;

    @Test
    void returnsCredentialOnlyForTheCurrentExecution() {
        CredentialCipher cipher = new CredentialCipher(KEY);
        Agent agent = agent();
        ManagedDatabase database = database();
        DatabaseCredential credential = new DatabaseCredential(3L, "backup",
                cipher.encrypt("secret"));
        ReflectionTestUtils.setField(credential, "id", 7L);
        OperationTask task = OperationTask.createForJob(1L, 10L, 7L,
                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");
        task.claim(LocalDateTime.now(CLOCK), LocalDateTime.now(CLOCK).plusSeconds(60));
        when(agents.findById(1L)).thenReturn(Optional.of(agent));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
        when(credentials.findById(7L)).thenReturn(Optional.of(credential));
        when(databases.findById(3L)).thenReturn(Optional.of(database));
        TaskCredentialService service = new TaskCredentialService(agents, tasks, credentials,
                databases, cipher, CLOCK);

        var response = service.resolve(1L, 20L,
                new ResolveTaskCredentialRequest("token", 1));

        assertThat(response.username()).isEqualTo("backup");
        assertThat(response.password()).isEqualTo("secret");
        assertThatThrownBy(() -> service.resolve(1L, 20L,
                new ResolveTaskCredentialRequest("token", 2)))
                .isInstanceOf(TaskExecutionConflictException.class);
    }

    private Agent agent() {
        Agent agent = Agent.register("agent", "host", "127.0.0.1", "Linux", "1", "token");
        ReflectionTestUtils.setField(agent, "id", 1L);
        return agent;
    }

    private ManagedDatabase database() {
        ManagedDatabase database = ManagedDatabase.register(new RegisterManagedDatabaseRequest(
                "orders", "mysql", 3306, "orders", DatabaseEngine.MYSQL, "prod", null, null, null));
        ReflectionTestUtils.setField(database, "id", 3L);
        database.assignAgent(1L);
        return database;
    }
}
