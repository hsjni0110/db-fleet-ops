package com.dbfleetops.operation.application;

import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.CreateBackupJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import com.dbfleetops.operation.infra.OperationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationJobService {

    private final ManagedDatabaseRepository databaseRepository;
    private final OperationJobRepository jobRepository;

    public OperationJobService(
            ManagedDatabaseRepository databaseRepository,
            OperationJobRepository jobRepository
    ) {
        this.databaseRepository = databaseRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public OperationJobResponse createBackupJob(
            Long databaseId,
            String idempotencyKey,
            CreateBackupJobRequest request
    ) {
        ManagedDatabase database =
                databaseRepository.findById(databaseId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Database not found. databaseId=" + databaseId
                        ));

        if (!database.isActive()) {
            throw new IllegalStateException(
                    "Inactive database cannot create operation job. databaseId="
                            + databaseId
            );
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return jobRepository
                    .findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(
                            databaseId,
                            JobType.BACKUP,
                            idempotencyKey
                    )
                    .map(OperationJobResponse::from)
                    .orElseGet(() -> createAndSaveBackupJob(
                            databaseId,
                            idempotencyKey,
                            request
                    ));
        }

        return createAndSaveBackupJob(
                databaseId,
                null,
                request
        );
    }

    @Transactional(readOnly = true)
    public OperationJobResponse getJob(
            Long jobId
    ) {
        OperationJob job =
                jobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Operation job not found. jobId=" + jobId
                        ));

        return OperationJobResponse.from(job);
    }

    private OperationJobResponse createAndSaveBackupJob(
            Long databaseId,
            String idempotencyKey,
            CreateBackupJobRequest request
    ) {
        OperationJob job =
                OperationJob.create(
                        JobType.BACKUP,
                        databaseId,
                        request.requestedBy(),
                        idempotencyKey,
                        toPayload(request)
                );

        OperationJob savedJob =
                jobRepository.save(job);

        return OperationJobResponse.from(savedJob);
    }

    private String toPayload(
            CreateBackupJobRequest request
    ) {
        return """
                {"reason":"%s","requestedBy":"%s"}
                """.formatted(
                safe(request.reason()),
                safe(request.requestedBy())
        ).trim();
    }

    private String safe(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value.replace("\"", "\\\"");
    }
}