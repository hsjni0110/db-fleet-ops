package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.JobOperations;
import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.CreateBackupJobRequest;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.operation.dto.CreateConfigurationCheckJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobService implements JobOperations {

        private final DatabaseReader databaseRepository;
        private final JobStore jobRepository;
        private final AuditWriter auditRecorderPort;
        private final ConfigurationJobRunner configurationJobs;

        public JobService(DatabaseReader databaseRepository,
                        JobStore jobRepository, AuditWriter auditRecorderPort,
                        ConfigurationJobRunner configurationJobs) {
                this.databaseRepository = databaseRepository;
                this.jobRepository = jobRepository;
                this.auditRecorderPort = auditRecorderPort;
                this.configurationJobs = configurationJobs;
        }

        @Transactional
        public OperationJobResponse createBackupJob(Long databaseId, String idempotencyKey,
                        CreateBackupJobRequest request) {
                DatabaseExecutionTarget database = getActiveDatabaseOrThrow(databaseId);

                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        return jobRepository
                                        .findDuplicate(
                                                        databaseId, JobType.BACKUP, idempotencyKey)
                                        .map(OperationJobResponse::from)
                                                        .orElseGet(() -> createAndSaveBackupJob(database.id(),
                                                        idempotencyKey, request));
                }

                return createAndSaveBackupJob(database.id(), null, request);
        }

        @Transactional
        public OperationJobResponse createConfigurationCheckJob(Long databaseId,
                        String idempotencyKey, CreateConfigurationCheckJobRequest request) {
                validateConfigurationCheckRequest(request);

                DatabaseExecutionTarget database = getActiveDatabaseOrThrow(databaseId);

                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        return jobRepository
                                        .findDuplicate(
                                                        databaseId, JobType.CONFIGURATION_CHECK,
                                                        idempotencyKey)
                                        .map(OperationJobResponse::from)
                                        .orElseGet(() -> createAndSaveConfigurationCheckJob(
                                                        database.id(), idempotencyKey, request));
                }

                return createAndSaveConfigurationCheckJob(database.id(), null, request);
        }

        @Transactional
        public OperationJobResponse createConfigurationApplyJob(Long databaseId,
                        String idempotencyKey, CreateConfigurationApplyJobRequest request) {
                validateConfigurationApplyRequest(request);

                DatabaseExecutionTarget database = getActiveDatabaseOrThrow(databaseId);

                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        return jobRepository
                                        .findDuplicate(
                                                        databaseId, JobType.CONFIGURATION_APPLY,
                                                        idempotencyKey)
                                        .map(OperationJobResponse::from).orElseGet(() -> {
                                                configurationJobs.validateApply(database.id(),
                                                                request);

                                                return createAndSaveConfigurationApplyJob(
                                                                database.id(), idempotencyKey,
                                                                request);
                                        });
                }

                configurationJobs.validateApply(database.id(), request);

                return createAndSaveConfigurationApplyJob(database.id(), null, request);
        }

        @Transactional(readOnly = true)
        public OperationJobResponse getJob(Long jobId) {
                OperationJob job = jobRepository.findById(jobId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Operation job not found. jobId=" + jobId));

                return OperationJobResponse.from(job);
        }

        @Transactional(readOnly = true)
        public List<OperationJobResponse> getJobs() {
                return jobRepository.findLatest().stream()
                                .map(OperationJobResponse::from)
                                .toList();
        }

        private DatabaseExecutionTarget getActiveDatabaseOrThrow(Long databaseId) {
                DatabaseExecutionTarget database = databaseRepository.findDatabase(databaseId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Database not found. databaseId=" + databaseId));

                if (!database.active()) {
                        throw new IllegalStateException("비활성화된 데이터베이스는 운영 작업을 수행할 수 없습니다.");
                }

                return database;
        }

        private OperationJobResponse createAndSaveBackupJob(Long databaseId, String idempotencyKey,
                        CreateBackupJobRequest request) {
                OperationJob job = OperationJob.create(JobType.BACKUP, databaseId,
                                request.requestedBy(), idempotencyKey, toBackupPayload(request));

                OperationJob savedJob = jobRepository.save(job);

                auditRecorderPort.record(request.requestedBy(), "JOB_CREATED", "OPERATION_JOB",
                                String.valueOf(savedJob.getId()), "SUCCESS",
                                "Backup job created. databaseId=" + databaseId);

                return OperationJobResponse.from(savedJob);
        }

        private OperationJobResponse createAndSaveConfigurationCheckJob(Long databaseId,
                        String idempotencyKey, CreateConfigurationCheckJobRequest request) {
                OperationJob job = OperationJob.create(JobType.CONFIGURATION_CHECK, databaseId,
                                request.requestedBy(), idempotencyKey,
                                toConfigurationCheckPayload(request));

                OperationJob savedJob = jobRepository.save(job);

                auditRecorderPort.record(request.requestedBy(), "JOB_CREATED", "OPERATION_JOB",
                                String.valueOf(savedJob.getId()), "SUCCESS",
                                "Configuration check job created. databaseId=" + databaseId
                                                + ", profileId=" + request.profileId());

                return OperationJobResponse.from(savedJob);
        }

        private OperationJobResponse createAndSaveConfigurationApplyJob(Long databaseId,
                        String idempotencyKey, CreateConfigurationApplyJobRequest request) {
                OperationJob job = OperationJob.create(JobType.CONFIGURATION_APPLY, databaseId,
                                request.requestedBy(), idempotencyKey,
                                toConfigurationApplyPayload(request));

                OperationJob savedJob = jobRepository.save(job);

                auditRecorderPort.record(request.requestedBy(), "JOB_CREATED", "OPERATION_JOB",
                                String.valueOf(savedJob.getId()), "SUCCESS",
                                "Configuration apply job created. databaseId=" + databaseId
                                                + ", profileId=" + request.profileId()
                                                + ", parameterCount="
                                                + request.parameters().size());

                return OperationJobResponse.from(savedJob);
        }

        private void validateConfigurationCheckRequest(CreateConfigurationCheckJobRequest request) {
                if (request == null) {
                        throw new IllegalArgumentException("request is required.");
                }

                if (request.profileId() == null) {
                        throw new IllegalArgumentException("profileId is required.");
                }

                if (request.requestedBy() == null || request.requestedBy().isBlank()) {
                        throw new IllegalArgumentException("requestedBy is required.");
                }
        }

        private void validateConfigurationApplyRequest(CreateConfigurationApplyJobRequest request) {
                if (request == null) {
                        throw new IllegalArgumentException("request is required.");
                }

                if (request.profileId() == null) {
                        throw new IllegalArgumentException("profileId is required.");
                }

                if (request.requestedBy() == null || request.requestedBy().isBlank()) {
                        throw new IllegalArgumentException("requestedBy is required.");
                }

                if (request.parameters() == null || request.parameters().isEmpty()) {
                        throw new IllegalArgumentException("parameters is required.");
                }

                request.parameters().forEach(parameter -> {
                        if (parameter == null) {
                                throw new IllegalArgumentException("parameter is required.");
                        }

                        if (parameter.parameterName() == null
                                        || parameter.parameterName().isBlank()) {
                                throw new IllegalArgumentException("parameterName is required.");
                        }

                        if (parameter.targetValue() == null || parameter.targetValue().isBlank()) {
                                throw new IllegalArgumentException("targetValue is required.");
                        }
                });
        }

        private String toBackupPayload(CreateBackupJobRequest request) {
                return """
                                {"reason":"%s","requestedBy":"%s"}
                                """.formatted(safe(request.reason()), safe(request.requestedBy()))
                                .trim();
        }

        private String toConfigurationCheckPayload(CreateConfigurationCheckJobRequest request) {
                return """
                                {"profileId":%d,"reason":"%s","requestedBy":"%s"}
                                """.formatted(request.profileId(), safe(request.reason()),
                                safe(request.requestedBy())).trim();
        }

        private String toConfigurationApplyPayload(CreateConfigurationApplyJobRequest request) {
                String parameterPayload = request.parameters().stream()
                                .map(parameter -> """
                                                {"parameterName":"%s","targetValue":"%s"}
                                                """
                                                .formatted(safe(parameter.parameterName()),
                                                                safe(parameter.targetValue()))
                                                .trim())
                                .reduce((left, right) -> left + "," + right).orElse("");

                return """
                                {"profileId":%d,"reason":"%s","requestedBy":"%s","parameters":[%s]}
                                """
                                .formatted(request.profileId(), safe(request.reason()),
                                                safe(request.requestedBy()), parameterPayload)
                                .trim();
        }

        private String safe(String value) {
                if (value == null) {
                        return "";
                }

                return value.replace("\"", "\\\"");
        }
}
