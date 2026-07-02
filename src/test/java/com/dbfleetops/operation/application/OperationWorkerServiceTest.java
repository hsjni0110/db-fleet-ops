package com.dbfleetops.operation.application;

import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import com.dbfleetops.operation.infra.OperationJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationWorkerServiceTest {

    @Mock
    private OperationJobRepository jobRepository;

    @Test
    void claimJobReturnsEmptyWhenNoQueuedJobExists() {
        when(jobRepository
                .findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                        eq(JobStatus.QUEUED),
                        any(LocalDateTime.class)
                ))
                .thenReturn(List.of());

        OperationWorkerService service =
                new OperationWorkerService(jobRepository);

        ClaimJobResponse response =
                service.claimJob("worker-1");

        assertThat(response.claimed())
                .isFalse();

        assertThat(response.jobId())
                .isNull();
    }

    @Test
    void claimJobChangesQueuedJobToRunningAndSetsLease() {
        OperationJob job =
                OperationJob.create(
                        JobType.BACKUP,
                        1L,
                        "local-user",
                        "idem-001"
                );

        when(jobRepository
                .findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                        eq(JobStatus.QUEUED),
                        any(LocalDateTime.class)
                ))
                .thenReturn(List.of(job));

        OperationWorkerService service =
                new OperationWorkerService(jobRepository);

        ClaimJobResponse response =
                service.claimJob("worker-1");

        assertThat(response.claimed())
                .isTrue();

        assertThat(response.jobType())
                .isEqualTo(JobType.BACKUP);

        assertThat(response.status())
                .isEqualTo(JobStatus.RUNNING);

        assertThat(response.targetDatabaseId())
                .isEqualTo(1L);

        assertThat(response.leaseOwner())
                .isEqualTo("worker-1");

        assertThat(response.leaseUntil())
                .isNotNull();

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.RUNNING);

        assertThat(job.getLeaseOwner())
                .isEqualTo("worker-1");

        assertThat(job.getLeaseUntil())
                .isNotNull();
    }
}