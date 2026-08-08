package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.JobOperations;
import com.dbfleetops.operation.application.required.AuditWriter;
import com.dbfleetops.operation.application.required.ConfigurationJobRunner;
import com.dbfleetops.operation.application.required.DatabaseExecutionTarget;
import com.dbfleetops.operation.application.required.DatabaseReader;
import com.dbfleetops.operation.application.required.JobStore;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ConfigurationApplyParameterRequest;
import com.dbfleetops.operation.dto.CreateBackupJobRequest;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.operation.dto.CreateConfigurationCheckJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.dbfleetops.operation.domain.JobType.BACKUP;
import static com.dbfleetops.operation.domain.JobType.CONFIGURATION_APPLY;
import static com.dbfleetops.operation.domain.JobType.CONFIGURATION_CHECK;
import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.noNullElements;
import static org.springframework.util.Assert.notEmpty;
import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;

@Service
public class JobService implements JobOperations {

    private final DatabaseReader databases;
    private final JobStore jobs;
    private final AuditWriter audit;
    private final ConfigurationJobRunner configurations;
    private final JobPayloadFactory payloads;

    public JobService(DatabaseReader databases, JobStore jobs, AuditWriter audit,
            ConfigurationJobRunner configurations, JobPayloadFactory payloads) {
        this.databases = databases;
        this.jobs = jobs;
        this.audit = audit;
        this.configurations = configurations;
        this.payloads = payloads;
    }

    @Override
    @Transactional
    public OperationJobResponse createBackupJob(Long databaseId, String duplicateKey,
            CreateBackupJobRequest request) {
        validateBackupRequest(request);

        DatabaseExecutionTarget database = requireActiveDatabase(databaseId);

        return submitBackup(database.id(), duplicateKey, request);
    }

    @Override
    @Transactional
    public OperationJobResponse createConfigurationCheckJob(Long databaseId, String duplicateKey,
            CreateConfigurationCheckJobRequest request) {
        validateCheckRequest(request);

        DatabaseExecutionTarget database = requireActiveDatabase(databaseId);

        return submitConfigurationCheck(database.id(), duplicateKey, request);
    }

    @Override
    @Transactional
    public OperationJobResponse createConfigurationApplyJob(Long databaseId, String duplicateKey,
            CreateConfigurationApplyJobRequest request) {
        validateApplyRequest(request);

        DatabaseExecutionTarget database = requireActiveDatabase(databaseId);

        return submitConfigurationApply(database.id(), duplicateKey, request);
    }

    @Override
    @Transactional(readOnly = true)
    public OperationJobResponse getJob(Long jobId) {
        notNull(jobId, "Job ID는 필수입니다.");

        OperationJob job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Job을 찾을 수 없습니다. jobId=" + jobId));

        return OperationJobResponse.from(job);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperationJobResponse> getJobs() {
        return jobs.findLatest().stream()
                .map(OperationJobResponse::from)
                .toList();
    }

    private OperationJobResponse submitBackup(Long databaseId, String duplicateKey,
            CreateBackupJobRequest request) {
        Optional<OperationJob> existingJob = findDuplicateJob(databaseId, BACKUP, duplicateKey);

        if (existingJob.isPresent()) {
            return OperationJobResponse.from(existingJob.get());
        }

        return saveBackup(databaseId, duplicateKey, request);
    }

    private OperationJobResponse submitConfigurationCheck(Long databaseId, String duplicateKey,
            CreateConfigurationCheckJobRequest request) {
        Optional<OperationJob> existingJob = findDuplicateJob(
                databaseId, CONFIGURATION_CHECK, duplicateKey);

        if (existingJob.isPresent()) {
            return OperationJobResponse.from(existingJob.get());
        }

        return saveConfigurationCheck(databaseId, duplicateKey, request);
    }

    private OperationJobResponse submitConfigurationApply(Long databaseId, String duplicateKey,
            CreateConfigurationApplyJobRequest request) {
        Optional<OperationJob> existingJob = findDuplicateJob(
                databaseId, CONFIGURATION_APPLY, duplicateKey);

        if (existingJob.isPresent()) {
            return OperationJobResponse.from(existingJob.get());
        }

        configurations.validateApply(databaseId, request);

        return saveConfigurationApply(databaseId, duplicateKey, request);
    }

    private Optional<OperationJob> findDuplicateJob(Long databaseId, JobType jobType,
            String duplicateKey) {
        if (!org.springframework.util.StringUtils.hasText(duplicateKey)) {
            return Optional.empty();
        }

        return jobs.findDuplicate(databaseId, jobType, duplicateKey);
    }

    private DatabaseExecutionTarget requireActiveDatabase(Long databaseId) {
        notNull(databaseId, "관리 DB ID는 필수입니다.");

        DatabaseExecutionTarget database = databases.findDatabase(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "관리 DB를 찾을 수 없습니다. databaseId=" + databaseId));

        state(database.active(), "비활성 관리 DB에는 Job을 생성할 수 없습니다.");

        return database;
    }

    private OperationJobResponse saveBackup(Long databaseId, String duplicateKey,
            CreateBackupJobRequest request) {
        OperationJob job = OperationJob.create(
                BACKUP,
                databaseId,
                request.requestedBy(),
                duplicateKey,
                payloads.backup(request));

        String message = "Backup job created. databaseId=" + databaseId;

        return saveCreatedJob(job, request.requestedBy(), message);
    }

    private OperationJobResponse saveConfigurationCheck(Long databaseId, String duplicateKey,
            CreateConfigurationCheckJobRequest request) {
        OperationJob job = OperationJob.create(
                CONFIGURATION_CHECK,
                databaseId,
                request.requestedBy(),
                duplicateKey,
                payloads.configurationCheck(request));

        String message = "Configuration check job created. databaseId=" + databaseId
                + ", profileId=" + request.profileId();

        return saveCreatedJob(job, request.requestedBy(), message);
    }

    private OperationJobResponse saveConfigurationApply(Long databaseId, String duplicateKey,
            CreateConfigurationApplyJobRequest request) {
        OperationJob job = OperationJob.create(
                CONFIGURATION_APPLY,
                databaseId,
                request.requestedBy(),
                duplicateKey,
                payloads.configurationApply(request));

        String message = "Configuration apply job created. databaseId=" + databaseId
                + ", profileId=" + request.profileId()
                + ", parameterCount=" + request.parameters().size();

        return saveCreatedJob(job, request.requestedBy(), message);
    }

    private OperationJobResponse saveCreatedJob(OperationJob job, String requestedBy,
            String auditMessage) {
        OperationJob savedJob = jobs.save(job);

        audit.record(
                requestedBy,
                "JOB_CREATED",
                "OPERATION_JOB",
                String.valueOf(savedJob.getId()),
                "SUCCESS",
                auditMessage);

        return OperationJobResponse.from(savedJob);
    }

    private void validateBackupRequest(CreateBackupJobRequest request) {
        notNull(request, "백업 요청은 필수입니다.");
        hasText(request.requestedBy(), "요청자는 필수입니다.");
    }

    private void validateCheckRequest(CreateConfigurationCheckJobRequest request) {
        notNull(request, "설정 점검 요청은 필수입니다.");
        notNull(request.profileId(), "설정 기준 ID는 필수입니다.");
        hasText(request.requestedBy(), "요청자는 필수입니다.");
    }

    private void validateApplyRequest(CreateConfigurationApplyJobRequest request) {
        notNull(request, "설정 적용 요청은 필수입니다.");
        notNull(request.profileId(), "설정 기준 ID는 필수입니다.");
        hasText(request.requestedBy(), "요청자는 필수입니다.");
        notEmpty(request.parameters(), "적용할 설정 항목은 필수입니다.");
        noNullElements(request.parameters(), "설정 항목은 null일 수 없습니다.");

        request.parameters().forEach(this::validateParameter);
    }

    private void validateParameter(ConfigurationApplyParameterRequest parameter) {
        hasText(parameter.parameterName(), "설정 항목 이름은 필수입니다.");
        hasText(parameter.targetValue(), "적용할 설정값은 필수입니다.");
    }
}
