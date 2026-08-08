package com.dbfleetops.operation.application.workflow.backup;

import com.dbfleetops.operation.application.*;
import com.dbfleetops.operation.application.provided.BackupTasks;
import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.*;
import com.dbfleetops.operation.dto.*;
import org.springframework.stereotype.Component;

/** 백업과 복원 검증 Task의 순서 및 Job 종료를 관리합니다. */
@Component
public class BackupWorkflow implements BackupTasks, TaskResultHandler {
    private final AgentReader agents;
    private final DatabaseReader databases;
    private final CredentialReader credentials;
    private final TaskStore tasks;
    private final JobTaskCoordinator coordinator;
    private final BackupPayloadBuilder payloads;
    private final BackupVerificationWriter verifications;
    public BackupWorkflow(AgentReader agents, DatabaseReader databases, CredentialReader credentials,
            TaskStore tasks, JobTaskCoordinator coordinator, BackupPayloadBuilder payloads,
            BackupVerificationWriter verifications) {
        this.agents = agents; this.databases = databases; this.credentials = credentials;
        this.tasks = tasks; this.coordinator = coordinator; this.payloads = payloads;
        this.verifications = verifications;
    }
    public boolean supports(OperationTaskType type) {
        return type == OperationTaskType.MYSQL_LOGICAL_BACKUP
                || type == OperationTaskType.MYSQL_RESTORE_VERIFY;
    }
    public OperationTaskResponse createBackupTask(Long jobId, Long databaseId) {
        if (tasks.existsByJobAndType(jobId, OperationTaskType.MYSQL_LOGICAL_BACKUP))
            return OperationTaskResponse.from(tasks.findByJob(jobId).stream()
                    .filter(task -> task.getTaskType() == OperationTaskType.MYSQL_LOGICAL_BACKUP)
                    .findFirst().orElseThrow());
        DatabaseExecutionTarget database = databases.findDatabase(databaseId).orElseThrow(() ->
                new IllegalArgumentException("Database not found. databaseId=" + databaseId));
        if (database.assignedAgentId() == null) throw new IllegalStateException(
                "Database has no assigned Agent. databaseId=" + databaseId);
        AgentExecutionTarget agent = agents.findAgent(database.assignedAgentId()).orElseThrow(() ->
                new IllegalStateException("Assigned Agent not found. agentId=" + database.assignedAgentId()));
        if (!agent.online()) throw new IllegalStateException("Assigned Agent is not ONLINE. agentId=" + agent.id());
        CredentialReference credential = credentials.findCredentialByDatabase(databaseId).orElseThrow(() ->
                new IllegalArgumentException("Credential not found. databaseId=" + databaseId));
        String parameters = """
                {"operationJobId":%d,"databaseId":%d,"databaseName":"%s","host":"%s","port":%d,
                "backupType":"LOGICAL","compression":true,"verifyAfterBackup":true,
                "verifyRowCount":true,"cleanup":true}
                """.formatted(jobId, databaseId, escape(database.databaseName()),
                        escape(database.host()), database.port());
        return OperationTaskResponse.from(tasks.save(OperationTask.createForJob(agent.id(), jobId,
                credential.id(), OperationTaskType.MYSQL_LOGICAL_BACKUP, parameters)));
    }
    public void handle(OperationTask task, String result) {
        OperationJob job = coordinator.linkedJobForUpdate(task);
        if (job == null) return;
        if (task.getTaskType() == OperationTaskType.MYSQL_LOGICAL_BACKUP) {
            if (!payloads.shouldVerifyAfterBackup(task.getParametersJson())) { job.succeed(result); return; }
            if (!tasks.existsByJobAndType(job.getId(), OperationTaskType.MYSQL_RESTORE_VERIFY)) {
                String payload = payloads.createRestorePayload(job.getId(), task.getId(),
                        task.getParametersJson(), result);
                OperationTask restoreTask = task.getCredentialId() == null
                        ? OperationTask.createForJob(task.getAgentId(), job.getId(),
                                OperationTaskType.MYSQL_RESTORE_VERIFY, payload)
                        : OperationTask.createForJob(task.getAgentId(), job.getId(),
                                task.getCredentialId(), OperationTaskType.MYSQL_RESTORE_VERIFY,
                                payload);
                tasks.save(restoreTask);
            }
            return;
        }
        RestoreVerificationOutcome outcome = verifications.record(job.getId(), task.getId(), result);
        if (outcome.verified()) job.succeed(result);
        else job.fail(outcome.errorCode(), outcome.errorMessage());
    }
    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
