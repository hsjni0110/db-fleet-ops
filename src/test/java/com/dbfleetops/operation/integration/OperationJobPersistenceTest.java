package com.dbfleetops.operation.integration;

import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.infra.OperationJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OperationJobPersistenceTest {

    @Autowired
    private OperationJobRepository jobRepository;

    @Test
    void saveAndFindOperationJob() {
        OperationJob job = OperationJob.create(
                JobType.BACKUP,
                1L,
                "local-user",
                "idem-persistence-001",
                "{\"reason\":\"persistence test\"}"
        );

        OperationJob savedJob =
                jobRepository.save(job);

        Optional<OperationJob> found =
                jobRepository.findById(savedJob.getId());

        assertThat(found)
                .isPresent();

        assertThat(found.get().getJobType())
                .isEqualTo(JobType.BACKUP);

        assertThat(found.get().getStatus())
                .isEqualTo(JobStatus.QUEUED);

        assertThat(found.get().getTargetDatabaseId())
                .isEqualTo(1L);

        assertThat(found.get().getRequestedBy())
                .isEqualTo("local-user");

        assertThat(found.get().getIdempotencyKey())
                .isEqualTo("idem-persistence-001");
    }

    @Test
    void findByIdempotencyKeyReturnsExistingJob() {
        OperationJob job = OperationJob.create(
                JobType.BACKUP,
                1L,
                "local-user",
                "idem-persistence-002",
                "{\"reason\":\"idempotency test\"}"
        );

        jobRepository.save(job);

        Optional<OperationJob> found =
                jobRepository.findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(
                        1L,
                        JobType.BACKUP,
                        "idem-persistence-002"
                );

        assertThat(found)
                .isPresent();

        assertThat(found.get().getJobType())
                .isEqualTo(JobType.BACKUP);

        assertThat(found.get().getStatus())
                .isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void updateJobStatusIsPersisted() {
        OperationJob job = OperationJob.create(
                JobType.BACKUP,
                1L,
                "local-user",
                "idem-persistence-003",
                "{\"reason\":\"status update test\"}"
        );

        OperationJob savedJob =
                jobRepository.save(job);

        savedJob.start(
                "worker-1",
                LocalDateTime.now().plusSeconds(60)
        );

        jobRepository.flush();

        OperationJob found =
                jobRepository.findById(savedJob.getId())
                        .orElseThrow();

        assertThat(found.getStatus())
                .isEqualTo(JobStatus.RUNNING);

        assertThat(found.getLeaseOwner())
                .isEqualTo("worker-1");

        assertThat(found.getLeaseUntil())
                .isNotNull();
    }
}