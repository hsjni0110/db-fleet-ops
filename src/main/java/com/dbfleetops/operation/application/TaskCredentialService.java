package com.dbfleetops.operation.application;

import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.database.application.CredentialCipher;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.dto.ResolveTaskCredentialRequest;
import com.dbfleetops.operation.dto.TaskCredentialResponse;
import com.dbfleetops.operation.exception.TaskExecutionConflictException;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class TaskCredentialService {
    private final AgentRepository agentRepository;
    private final OperationTaskRepository taskRepository;
    private final DatabaseCredentialRepository credentialRepository;
    private final ManagedDatabaseRepository databaseRepository;
    private final CredentialCipher cipher;
    private final Clock clock;

    public TaskCredentialService(AgentRepository agentRepository,
            OperationTaskRepository taskRepository,
            DatabaseCredentialRepository credentialRepository,
            ManagedDatabaseRepository databaseRepository, CredentialCipher cipher, Clock clock) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.credentialRepository = credentialRepository;
        this.databaseRepository = databaseRepository;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Transactional
    public TaskCredentialResponse resolve(Long agentId, Long taskId,
            ResolveTaskCredentialRequest request) {
        var agent = agentRepository.findById(agentId).orElseThrow(
                () -> new TaskExecutionConflictException("Agent not found. agentId=" + agentId));
        if (!agent.matchesToken(request.agentToken())) {
            throw new TaskExecutionConflictException("Invalid Agent token.");
        }
        var task = taskRepository.findById(taskId).orElseThrow(
                () -> new TaskExecutionConflictException("Task not found. taskId=" + taskId));
        if (!agentId.equals(task.getAgentId())) {
            throw new TaskExecutionConflictException("Task does not belong to Agent.");
        }
        try {
            task.validateCredentialAccess(request.executionAttempt(), LocalDateTime.now(clock));
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new TaskExecutionConflictException(exception.getMessage(), exception);
        }
        var credential = credentialRepository.findById(task.getCredentialId()).orElseThrow(
                () -> new TaskExecutionConflictException("Credential not found."));
        var database = databaseRepository.findById(credential.getDatabaseId()).orElseThrow(
                () -> new TaskExecutionConflictException("Database not found."));
        if (!agentId.equals(database.getAssignedAgentId())) {
            throw new TaskExecutionConflictException("Database is assigned to another Agent.");
        }
        return new TaskCredentialResponse(credential.getUsername(),
                credential.revealPassword(cipher));
    }
}
