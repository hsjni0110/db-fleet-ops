package com.dbfleetops.operation.workflow.backup;

import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.shared.application.required.AgentExecutionTarget;
import com.dbfleetops.operation.shared.application.required.AgentReader;
import com.dbfleetops.operation.shared.application.required.CredentialReader;
import com.dbfleetops.operation.shared.application.required.CredentialReference;
import com.dbfleetops.operation.shared.application.required.DatabaseExecutionTarget;
import com.dbfleetops.operation.shared.application.required.DatabaseReader;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import com.dbfleetops.operation.task.dto.OperationTaskResponse;
import com.dbfleetops.operation.workflow.application.provided.BackupStarter;
import com.dbfleetops.operation.workflow.application.provided.BackupTaskResults;
import com.dbfleetops.operation.workflow.application.required.BackupPayloadBuilder;
import com.dbfleetops.operation.workflow.application.required.BackupVerificationWriter;
import com.dbfleetops.operation.workflow.application.required.RestoreVerificationOutcome;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;

/** 백업과 복원 검증 Task의 순서 및 Job 종료를 관리합니다. */
@Component
public class BackupWorkflow implements BackupStarter, BackupTaskResults {

    private final AgentReader agents;
    private final DatabaseReader databases;
    private final CredentialReader credentials;
    private final JobStore jobs;
    private final TaskStore tasks;
    private final BackupPayloadBuilder payloads;
    private final BackupVerificationWriter verifications;
    private final Clock clock;

    public BackupWorkflow(AgentReader agents, DatabaseReader databases, CredentialReader credentials,
            JobStore jobs, TaskStore tasks, BackupPayloadBuilder payloads,
            BackupVerificationWriter verifications, Clock clock) {
        this.agents = agents;
        this.databases = databases;
        this.credentials = credentials;
        this.jobs = jobs;
        this.tasks = tasks;
        this.payloads = payloads;
        this.verifications = verifications;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OperationTaskResponse startBackup(Long jobId, Long databaseId) {
        validateTaskCreationRequest(jobId, databaseId);

        Optional<OperationTask> existingTask = findExistingBackupTask(jobId);
        if (existingTask.isPresent()) {
            return OperationTaskResponse.from(existingTask.get());
        }

        DatabaseExecutionTarget database = requireDatabase(databaseId);
        AgentExecutionTarget agent = requireAssignedAgent(database);
        CredentialReference credential = requireCredential(databaseId);

        OperationTask backupTask = createLogicalBackupTask(jobId, database, agent, credential);
        return OperationTaskResponse.from(tasks.save(backupTask));
    }

    @Override
    @Transactional
    public void continueAfterSuccess(Long taskId, String resultPayloadJson) {
        validateTaskResultRequest(taskId, resultPayloadJson);

        OperationTask task = requireTask(taskId);
        validateBackupTask(task);

        OperationJob job = findLinkedJobForUpdate(task);
        if (job == null) {
            return;
        }

        if (isLogicalBackup(task)) {
            handleBackupResult(job, task, resultPayloadJson);
            return;
        }

        handleRestoreVerificationResult(job, task, resultPayloadJson);
    }

    private OperationTask requireTask(Long taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Task를 찾을 수 없습니다. taskId=" + taskId));
    }

    private OperationJob findLinkedJobForUpdate(OperationTask task) {
        Long jobId = task.getOperationJobId();
        if (jobId == null) {
            return null;
        }

        return jobs.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "연결된 Job을 찾을 수 없습니다. jobId=" + jobId));
    }

    private Optional<OperationTask> findExistingBackupTask(Long jobId) {
        if (!tasks.existsByJobAndType(jobId, OperationTaskType.MYSQL_LOGICAL_BACKUP)) {
            return Optional.empty();
        }

        OperationTask existingTask = tasks.findByJob(jobId).stream()
                .filter(this::isLogicalBackup)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Backup Task 존재 여부와 조회 결과가 일치하지 않습니다. jobId=" + jobId));
        return Optional.of(existingTask);
    }

    private DatabaseExecutionTarget requireDatabase(Long databaseId) {
        return databases.findDatabase(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "관리 Database를 찾을 수 없습니다. databaseId=" + databaseId));
    }

    private AgentExecutionTarget requireAssignedAgent(DatabaseExecutionTarget database) {
        Long agentId = database.assignedAgentId();
        state(agentId != null,
                "관리 Database에 Agent가 배정되지 않았습니다. databaseId=" + database.id());

        AgentExecutionTarget agent = agents.findAgent(agentId)
                .orElseThrow(() -> new IllegalStateException(
                        "배정된 Agent를 찾을 수 없습니다. agentId=" + agentId));

        state(agent.online(), "배정된 Agent가 ONLINE 상태가 아닙니다. agentId=" + agent.id());
        return agent;
    }

    private CredentialReference requireCredential(Long databaseId) {
        return credentials.findCredentialByDatabase(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "DB 접속 인증 정보를 찾을 수 없습니다. databaseId=" + databaseId));
    }

    private OperationTask createLogicalBackupTask(Long jobId, DatabaseExecutionTarget database,
            AgentExecutionTarget agent, CredentialReference credential) {
        String parameters = createBackupParameters(jobId, database);

        return OperationTask.createForJob(agent.id(), jobId, credential.id(),
                OperationTaskType.MYSQL_LOGICAL_BACKUP, parameters);
    }

    private String createBackupParameters(Long jobId, DatabaseExecutionTarget database) {
        return """
                {"operationJobId":%d,"databaseId":%d,"databaseName":"%s","host":"%s","port":%d,
                "backupType":"LOGICAL","compression":true,"verifyAfterBackup":true,
                "verifyRowCount":true,"cleanup":true}
                """.formatted(jobId, database.id(), escape(database.databaseName()),
                        escape(database.host()), database.port());
    }

    private void handleBackupResult(OperationJob job, OperationTask backupTask,
            String resultPayloadJson) {
        if (!requiresRestoreVerification(backupTask)) {
            job.succeed(now(), resultPayloadJson);
            return;
        }

        createRestoreVerificationTask(job, backupTask, resultPayloadJson);
    }

    private boolean requiresRestoreVerification(OperationTask backupTask) {
        return payloads.shouldVerifyAfterBackup(backupTask.getParametersJson());
    }

    private void createRestoreVerificationTask(OperationJob job, OperationTask backupTask,
            String backupResultPayload) {
        if (tasks.existsByJobAndType(job.getId(), OperationTaskType.MYSQL_RESTORE_VERIFY)) {
            return;
        }

        String restoreParameters = payloads.createRestorePayload(job.getId(), backupTask.getId(),
                backupTask.getParametersJson(), backupResultPayload);
        OperationTask restoreTask = createRestoreTask(job, backupTask, restoreParameters);

        tasks.save(restoreTask);
    }

    private OperationTask createRestoreTask(OperationJob job, OperationTask backupTask,
            String restoreParameters) {
        if (backupTask.getCredentialId() == null) {
            return OperationTask.createForJob(backupTask.getAgentId(), job.getId(),
                    OperationTaskType.MYSQL_RESTORE_VERIFY, restoreParameters);
        }

        return OperationTask.createForJob(backupTask.getAgentId(), job.getId(),
                backupTask.getCredentialId(), OperationTaskType.MYSQL_RESTORE_VERIFY,
                restoreParameters);
    }

    private void handleRestoreVerificationResult(OperationJob job, OperationTask restoreTask,
            String resultPayloadJson) {
        RestoreVerificationOutcome outcome = verifications.record(job.getId(), restoreTask.getId(),
                resultPayloadJson);

        if (outcome.verified()) {
            job.succeed(now(), resultPayloadJson);
            return;
        }

        job.fail(now(), outcome.errorCode(), outcome.errorMessage());
    }

    private boolean isLogicalBackup(OperationTask task) {
        return task.getTaskType() == OperationTaskType.MYSQL_LOGICAL_BACKUP;
    }

    private void validateTaskCreationRequest(Long jobId, Long databaseId) {
        notNull(jobId, "Operation Job ID는 필수입니다.");
        notNull(databaseId, "Database ID는 필수입니다.");
    }

    private void validateTaskResultRequest(Long taskId, String resultPayloadJson) {
        notNull(taskId, "Task ID는 필수입니다.");
        hasText(resultPayloadJson, "Task 결과는 필수입니다.");
    }

    private void validateBackupTask(OperationTask task) {
        notNull(task, "Task는 필수입니다.");
        state(isBackupTask(task),
                "지원하지 않는 Task 종류입니다. taskType=" + task.getTaskType());
    }

    private boolean isBackupTask(OperationTask task) {
        return task.getTaskType() == OperationTaskType.MYSQL_LOGICAL_BACKUP
                || task.getTaskType() == OperationTaskType.MYSQL_RESTORE_VERIFY;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
