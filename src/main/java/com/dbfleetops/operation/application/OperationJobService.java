package com.dbfleetops.operation.application;

import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.CreateBackupJobRequest;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.operation.dto.CreateConfigurationCheckJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import com.dbfleetops.operation.infra.OperationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationJobService {

        private final ManagedDatabaseRepository databaseRepository;
        private final OperationJobRepository jobRepository;
        private final AuditRecorderPort auditRecorderPort;

        public OperationJobService(ManagedDatabaseRepository databaseRepository,
                        OperationJobRepository jobRepository, AuditRecorderPort auditRecorderPort) {
                this.databaseRepository = databaseRepository;
                this.jobRepository = jobRepository;
                this.auditRecorderPort = auditRecorderPort;
        }

        @Transactional
        public OperationJobResponse createBackupJob(Long databaseId, String idempotencyKey,
                        CreateBackupJobRequest request) {
                ManagedDatabase database = getActiveDatabaseOrThrow(databaseId);

                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        return jobRepository
                                        .findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(
                                                        databaseId, JobType.BACKUP, idempotencyKey)
                                        .map(OperationJobResponse::from)
                                        .orElseGet(() -> createAndSaveBackupJob(database.getId(),
                                                        idempotencyKey, request));
                }

                return createAndSaveBackupJob(database.getId(), null, request);
        }

        @Transactional
        public OperationJobResponse createConfigurationCheckJob(Long databaseId,
                        String idempotencyKey, CreateConfigurationCheckJobRequest request) {
                validateConfigurationCheckRequest(request);

                ManagedDatabase database = getActiveDatabaseOrThrow(databaseId);

                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        return jobRepository
                                        .findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(
                                                        databaseId, JobType.CONFIGURATION_CHECK,
                                                        idempotencyKey)
                                        .map(OperationJobResponse::from)
                                        .orElseGet(() -> createAndSaveConfigurationCheckJob(
                                                        database.getId(), idempotencyKey, request));
                }

                return createAndSaveConfigurationCheckJob(database.getId(), null, request);
        }

        @Transactional
        public OperationJobResponse createConfigurationApplyJob(Long databaseId,
                        String idempotencyKey, CreateConfigurationApplyJobRequest request) {
                validateConfigurationApplyRequest(request);

                ManagedDatabase database = getActiveDatabaseOrThrow(databaseId);

                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        return jobRepository
                                        .findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(
                                                        databaseId, JobType.CONFIGURATION_APPLY,
                                                        idempotencyKey)
                                        .map(OperationJobResponse::from)
                                        .orElseGet(() -> createAndSaveConfigurationApplyJob(
                                                        database.getId(), idempotencyKey, request));
                }

                return createAndSaveConfigurationApplyJob(database.getId(), null, request);
        }

        @Transactional(readOnly = true)
        public OperationJobResponse getJob(Long jobId) {
                OperationJob job = jobRepository.findById(jobId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Operation job not found. jobId=" + jobId));

                return OperationJobResponse.from(job);
        }

        private ManagedDatabase getActiveDatabaseOrThrow(Long databaseId) {
                ManagedDatabase database = databaseRepository.findById(databaseId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Database not found. databaseId=" + databaseId));

                if (!database.isActive()) {
                        throw new IllegalStateException(
                                        "Inactive database cannot create operation job. databaseId="
                                                        + databaseId);
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
                                {"reason":"%s","requestedBy":"%s","parameters":[%s]}
                                """.formatted(safe(request.reason()), safe(request.requestedBy()),
                                parameterPayload).trim();
        }

        private String safe(String value) {
                if (value == null) {
                        return "";
                }

                return value.replace("\"", "\\\"");
        }
}
