package com.dbfleetops.operation.task.application.service;

import com.dbfleetops.operation.task.application.provided.TaskCredential;
import com.dbfleetops.operation.shared.application.required.AgentReader;
import com.dbfleetops.operation.shared.application.required.CredentialReader;
import com.dbfleetops.operation.shared.application.required.CredentialReference;
import com.dbfleetops.operation.shared.application.required.DatabaseExecutionTarget;
import com.dbfleetops.operation.shared.application.required.DatabaseReader;
import com.dbfleetops.operation.shared.application.required.ResolvedTaskCredential;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.TaskExecutionConflictException;
import com.dbfleetops.operation.task.dto.ResolveTaskCredentialRequest;
import com.dbfleetops.operation.task.dto.TaskCredentialResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;

@Service
public class TaskCredentialService implements TaskCredential {

    private final AgentReader agents;
    private final TaskStore tasks;
    private final CredentialReader credentials;
    private final DatabaseReader databases;
    private final Clock clock;

    public TaskCredentialService(AgentReader agents, TaskStore tasks,
            CredentialReader credentials, DatabaseReader databases, Clock clock) {
        this.agents = agents;
        this.tasks = tasks;
        this.credentials = credentials;
        this.databases = databases;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TaskCredentialResponse resolve(Long agentId, Long taskId,
            ResolveTaskCredentialRequest request) {
        validateRequest(agentId, taskId, request);
        authenticateAgent(agentId, request.agentToken());

        OperationTask task = requireOwnedTask(agentId, taskId);

        task.validateCredentialAccess(request.executionAttempt(), now());

        CredentialReference credential = requireCredential(task);
        requireAssignedDatabase(agentId, credential);

        return resolveCredential(credential);
    }

    private CredentialReference requireCredential(OperationTask task) {
        return credentials.findCredential(task.getCredentialId())
                .orElseThrow(() -> conflict("Task의 DB 접속 인증 정보를 찾을 수 없습니다."));
    }

    private void requireAssignedDatabase(Long agentId, CredentialReference credential) {
        DatabaseExecutionTarget database = databases.findDatabase(credential.databaseId())
                .orElseThrow(() -> conflict(
                        "DB 접속 인증 정보에 연결된 관리 DB를 찾을 수 없습니다."));

        if (!agentId.equals(database.assignedAgentId())) {
            throw conflict("관리 DB가 다른 Agent에 배정되어 있습니다.");
        }
    }

    private TaskCredentialResponse resolveCredential(CredentialReference credential) {
        ResolvedTaskCredential resolved = credentials.resolve(credential.id());
        return new TaskCredentialResponse(resolved.username(), resolved.password());
    }

    private OperationTask requireOwnedTask(Long agentId, Long taskId) {
        OperationTask task = tasks.findById(taskId)
                .orElseThrow(() -> conflict("Task를 찾을 수 없습니다. taskId=" + taskId));

        if (!agentId.equals(task.getAgentId())) {
            throw conflict("다른 Agent에게 배정된 Task입니다.");
        }

        return task;
    }

    private void authenticateAgent(Long agentId, String agentToken) {
        agents.findAgent(agentId)
                .orElseThrow(() -> conflict("Agent를 찾을 수 없습니다. agentId=" + agentId));

        if (!agents.matchesToken(agentId, agentToken)) {
            throw conflict("Agent Token이 올바르지 않습니다.");
        }
    }

    private void validateRequest(Long agentId, Long taskId,
            ResolveTaskCredentialRequest request) {
        notNull(agentId, "Agent ID는 필수입니다.");
        notNull(taskId, "Task ID는 필수입니다.");
        notNull(request, "DB 접속 인증 정보 요청은 필수입니다.");
        hasText(request.agentToken(), "Agent Token은 필수입니다.");
    }

    private TaskExecutionConflictException conflict(String message) {
        return new TaskExecutionConflictException(message);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
