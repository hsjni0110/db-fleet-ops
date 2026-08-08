package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.TaskCredential;
import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.dto.ResolveTaskCredentialRequest;
import com.dbfleetops.operation.dto.TaskCredentialResponse;
import com.dbfleetops.operation.exception.TaskExecutionConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class TaskCredentialService implements TaskCredential {
    private final AgentReader agentRepository;
    private final TaskStore taskRepository;
    private final CredentialReader credentialRepository;
    private final DatabaseReader databaseRepository;
    private final Clock clock;

    public TaskCredentialService(AgentReader agentRepository,
            TaskStore taskRepository,
            CredentialReader credentialRepository,
            DatabaseReader databaseRepository, Clock clock) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.credentialRepository = credentialRepository;
        this.databaseRepository = databaseRepository;
        this.clock = clock;
    }

    @Transactional
    public TaskCredentialResponse resolve(Long agentId, Long taskId,
            ResolveTaskCredentialRequest request) {
        agentRepository.findAgent(agentId).orElseThrow(
                () -> new TaskExecutionConflictException("Agent not found. agentId=" + agentId));
        if (!agentRepository.matchesToken(agentId, request.agentToken())) {
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
        var credential = credentialRepository.findCredential(task.getCredentialId()).orElseThrow(
                () -> new TaskExecutionConflictException("Credential not found."));
        var database = databaseRepository.findDatabase(credential.databaseId()).orElseThrow(
                () -> new TaskExecutionConflictException("Database not found."));
        if (!agentId.equals(database.assignedAgentId())) {
            throw new TaskExecutionConflictException("Database is assigned to another Agent.");
        }
        var resolved = credentialRepository.resolve(credential.id());
        return new TaskCredentialResponse(resolved.username(), resolved.password());
    }
}
