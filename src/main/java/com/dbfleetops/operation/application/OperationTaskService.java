package com.dbfleetops.operation.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentHostMetric;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.infra.AgentHostMetricRepository;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.backup.application.BackupRestoreVerificationResultRecorder;
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
    private final AgentHostMetricRepository agentHostMetricRepository;
    private final RestoreVerifyTaskPayloadFactory restoreVerifyTaskPayloadFactory;
    private final BackupRestoreVerificationResultRecorder backupRestoreVerificationResultRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OperationTaskService(AgentRepository agentRepository,
            OperationTaskRepository taskRepository, OperationJobRepository jobRepository,
            AgentHostMetricRepository agentHostMetricRepository,
            RestoreVerifyTaskPayloadFactory restoreVerifyTaskPayloadFactory,
            BackupRestoreVerificationResultRecorder backupRestoreVerificationResultRecorder) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.jobRepository = jobRepository;
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

        /*
         * 현재 이 메서드는 databaseId만 받아서 Task를 생성하고 있음.
         *
         * 하지만 Go Agent의 MYSQL_LOGICAL_BACKUP handler는 실제로 아래 값이 필요함.
         *
         * - databaseName - host - port - username - password
         *
         * 따라서 이 메서드는 다음 커밋에서 ManagedDatabase / Credential을 조회하도록 확장하는 것이 맞음.
         *
         * 지금은 기존 흐름을 깨지 않기 위해 verifyAfterBackup=false로 둠. verifyAfterBackup=true를 사용하려면
         * parametersJson에 복원 검증에 필요한 접속 정보가 모두 들어가 있어야 함.
         */
        String parametersJson = """
                {
                  "operationJobId": %d,
                  "databaseId": %d,
                  "backupType": "LOGICAL",
                  "compression": true,
                  "verifyAfterBackup": false
                }
                """.formatted(operationJobId, databaseId);

        OperationTask task = OperationTask.createForJob(agent.getId(), operationJobId,
                OperationTaskType.MYSQL_LOGICAL_BACKUP, parametersJson);

        OperationTask savedTask = taskRepository.save(task);

        return OperationTaskResponse.from(savedTask);
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
