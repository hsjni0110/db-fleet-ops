package com.dbfleetops.operation.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentHostMetric;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.infra.AgentHostMetricRepository;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.backup.application.BackupRestoreVerificationResultRecorder;
import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.dto.CompleteOperationTaskRequest;
import com.dbfleetops.operation.dto.CreateOperationTaskRequest;
import com.dbfleetops.operation.dto.FailOperationTaskRequest;
import com.dbfleetops.operation.dto.MysqlBackupTaskPayload;
import com.dbfleetops.operation.dto.MysqlRestoreVerifyTaskResultPayload;
import com.dbfleetops.operation.dto.NextOperationTaskResponse;
import com.dbfleetops.operation.dto.OperationTaskResponse;
import com.dbfleetops.operation.dto.StartOperationTaskRequest;
import com.dbfleetops.operation.infra.OperationJobRepository;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationTaskService {

    private final AgentRepository agentRepository;
    private final OperationTaskRepository taskRepository;
    private final OperationJobRepository jobRepository;
    private final ManagedDatabaseRepository databaseRepository;
    private final DatabaseCredentialRepository credentialRepository;
    private final AgentHostMetricRepository agentHostMetricRepository;
    private final RestoreVerifyTaskPayloadFactory restoreVerifyTaskPayloadFactory;
    private final BackupRestoreVerificationResultRecorder backupRestoreVerificationResultRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OperationTaskService(AgentRepository agentRepository,
            OperationTaskRepository taskRepository, OperationJobRepository jobRepository,
            ManagedDatabaseRepository databaseRepository,
            DatabaseCredentialRepository credentialRepository,
            AgentHostMetricRepository agentHostMetricRepository,
            RestoreVerifyTaskPayloadFactory restoreVerifyTaskPayloadFactory,
            BackupRestoreVerificationResultRecorder backupRestoreVerificationResultRecorder) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.jobRepository = jobRepository;
        this.databaseRepository = databaseRepository;
        this.credentialRepository = credentialRepository;
        this.agentHostMetricRepository = agentHostMetricRepository;
        this.restoreVerifyTaskPayloadFactory = restoreVerifyTaskPayloadFactory;
        this.backupRestoreVerificationResultRecorder = backupRestoreVerificationResultRecorder;
    }

    @Transactional
    public OperationTaskResponse createTask(CreateOperationTaskRequest request) {
        Agent agent = agentRepository.findById(request.agentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Agent not found. agentId=" + request.agentId()));

        OperationTask task;

        if (request.operationJobId() == null) {
            task = OperationTask.create(agent.getId(), request.taskType(),
                    request.parametersJson());
        } else {
            task = OperationTask.createForJob(agent.getId(), request.operationJobId(),
                    request.taskType(), request.parametersJson());
        }

        OperationTask savedTask = taskRepository.save(task);

        return OperationTaskResponse.from(savedTask);
    }

    @Transactional(readOnly = true)
    public NextOperationTaskResponse nextTask(Long agentId, String agentToken) {
        getAgentAndValidateToken(agentId, agentToken);

        return taskRepository
                .findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(agentId, OperationTaskStatus.QUEUED)
                .stream().findFirst().map(NextOperationTaskResponse::from)
                .orElseGet(NextOperationTaskResponse::empty);
    }

    @Transactional
    public OperationTaskResponse startTask(Long agentId, Long taskId,
            StartOperationTaskRequest request) {
        getAgentAndValidateToken(agentId, request.agentToken());

        OperationTask task = getTaskOwnedByAgent(agentId, taskId);

        task.start();

        return OperationTaskResponse.from(task);
    }

    @Transactional
    public OperationTaskResponse completeTask(Long agentId, Long taskId,
            CompleteOperationTaskRequest request) {
        getAgentAndValidateToken(agentId, request.agentToken());

        OperationTask task = getTaskOwnedByAgent(agentId, taskId);

        task.complete(request.resultPayloadJson());

        persistLinuxStatusMetricIfNeeded(task, request.resultPayloadJson());

        OperationJob job = getLinkedOperationJob(task);

        if (job == null) {
            return OperationTaskResponse.from(task);
        }

        if (task.getTaskType() == OperationTaskType.MYSQL_LOGICAL_BACKUP) {
            handleLogicalBackupTaskCompleted(task, request.resultPayloadJson(), job);

            return OperationTaskResponse.from(task);
        }

        if (task.getTaskType() == OperationTaskType.MYSQL_RESTORE_VERIFY) {
            handleRestoreVerifyTaskCompleted(task, request.resultPayloadJson(), job);

            return OperationTaskResponse.from(task);
        }

        job.succeed(request.resultPayloadJson());

        return OperationTaskResponse.from(task);
    }

    private void handleLogicalBackupTaskCompleted(OperationTask backupTask,
            String resultPayloadJson, OperationJob job) {
        MysqlBackupTaskPayload backupTaskPayload = restoreVerifyTaskPayloadFactory
                .parseBackupTaskPayload(backupTask.getParametersJson());

        if (!backupTaskPayload.shouldVerifyAfterBackup()) {
            job.succeed(resultPayloadJson);

            return;
        }

        String restoreVerifyTaskPayloadJson = restoreVerifyTaskPayloadFactory
                .createRestoreVerifyTaskPayloadJson(backupTask.getOperationJobId(),
                        backupTask.getId(), backupTask.getParametersJson(), resultPayloadJson);

        OperationTask restoreVerifyTask =
                OperationTask.createForJob(backupTask.getAgentId(), backupTask.getOperationJobId(),
                        OperationTaskType.MYSQL_RESTORE_VERIFY, restoreVerifyTaskPayloadJson);

        taskRepository.save(restoreVerifyTask);

        /*
         * 중요: 여기서 OperationJob을 성공 처리하지 않는다.
         *
         * 기존: MYSQL_LOGICAL_BACKUP 성공 -> OperationJob SUCCEEDED
         *
         * 변경: MYSQL_LOGICAL_BACKUP 성공 -> MYSQL_RESTORE_VERIFY Task 생성 -> OperationJob은 RUNNING 유지
         * -> MYSQL_RESTORE_VERIFY 성공 시 OperationJob SUCCEEDED
         */
    }

    private void persistLinuxStatusMetricIfNeeded(OperationTask task, String resultPayloadJson) {
        if (task.getTaskType() != OperationTaskType.COLLECT_LINUX_STATUS) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(resultPayloadJson);

            AgentHostMetric metric = AgentHostMetric.create(task.getAgentId(),
                    root.path("cpuUsagePercent").asDouble(),
                    root.path("memoryUsagePercent").asDouble(),
                    root.path("diskUsagePercent").asDouble());

            agentHostMetricRepository.save(metric);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid linux status metric payload.", exception);
        }
    }

    @Transactional
    public OperationTaskResponse failTask(Long agentId, Long taskId,
            FailOperationTaskRequest request) {
        getAgentAndValidateToken(agentId, request.agentToken());

        OperationTask task = getTaskOwnedByAgent(agentId, taskId);

        task.fail(request.errorCode(), request.errorMessage());

        OperationJob job = getLinkedOperationJob(task);

        if (job != null) {
            job.fail(request.errorCode(), request.errorMessage());
        }

        return OperationTaskResponse.from(task);
    }

    private Agent getAgentAndValidateToken(Long agentId, String agentToken) {
        Agent agent = agentRepository.findById(agentId).orElseThrow(
                () -> new IllegalArgumentException("Agent not found. agentId=" + agentId));

        if (!agent.tokenMatches(agentToken)) {
            throw new IllegalArgumentException("Invalid agent token. agentId=" + agentId);
        }

        return agent;
    }

    private OperationTask getTaskOwnedByAgent(Long agentId, Long taskId) {
        OperationTask task = taskRepository.findById(taskId).orElseThrow(
                () -> new IllegalArgumentException("Operation task not found. taskId=" + taskId));

        if (!agentId.equals(task.getAgentId())) {
            throw new IllegalStateException("Task does not belong to agent. agentId=" + agentId
                    + ", taskAgentId=" + task.getAgentId());
        }

        return task;
    }

    @Transactional
    public OperationTaskResponse createBackupTaskForOperationJob(Long operationJobId,
            Long databaseId) {
        Agent agent =
                agentRepository.findFirstByStatusOrderByLastHeartbeatAtDesc(AgentStatus.ONLINE)
                        .orElseThrow(() -> new IllegalStateException(
                                "No ONLINE agent available for backup task."));

        ManagedDatabase database = databaseRepository.findById(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Database not found. databaseId=" + databaseId));

        DatabaseCredential credential = credentialRepository.findByDatabaseId(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Credential not found. databaseId=" + databaseId));

        String parametersJson = """
                {
                  "operationJobId": %d,
                  "databaseId": %d,
                  "databaseName": "%s",
                  "host": "%s",
                  "port": %d,
                  "username": "%s",
                  "password": "%s",
                  "backupType": "LOGICAL",
                  "compression": true,
                  "verifyAfterBackup": true,
                  "verifyRowCount": true,
                  "cleanup": true
                }
                """.formatted(operationJobId, databaseId, escapeJson(database.getDatabaseName()),
                escapeJson(database.getHost()), database.getPort(), escapeJson(credential.getUsername()),
                escapeJson(credential.getPassword()));

        OperationTask task = OperationTask.createForJob(agent.getId(), operationJobId,
                OperationTaskType.MYSQL_LOGICAL_BACKUP, parametersJson);

        OperationTask savedTask = taskRepository.save(task);

        return OperationTaskResponse.from(savedTask);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private OperationJob getLinkedOperationJob(OperationTask task) {
        if (task.getOperationJobId() == null) {
            return null;
        }

        return jobRepository.findById(task.getOperationJobId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Operation job not found. operationJobId=" + task.getOperationJobId()));
    }

    private void handleRestoreVerifyTaskCompleted(OperationTask restoreVerifyTask,
            String resultPayloadJson, OperationJob job) {
        MysqlRestoreVerifyTaskResultPayload resultPayload = backupRestoreVerificationResultRecorder
                .record(restoreVerifyTask, resultPayloadJson);

        if (resultPayload.isVerified()) {
            job.succeed(resultPayloadJson);

            return;
        }

        String errorCode = resultPayload.errorCode() == null || resultPayload.errorCode().isBlank()
                ? "RESTORE_VERIFY_FAILED"
                : resultPayload.errorCode();

        String errorMessage =
                resultPayload.errorMessage() == null || resultPayload.errorMessage().isBlank()
                        ? resultPayloadJson
                        : resultPayload.errorMessage();

        job.fail(errorCode, errorMessage);
    }
}
