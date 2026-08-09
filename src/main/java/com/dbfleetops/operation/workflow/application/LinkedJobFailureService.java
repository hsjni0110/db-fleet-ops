package com.dbfleetops.operation.workflow.application;

import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.task.application.required.LinkedJobFailure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Task의 실패와 최종 시간 초과를 연결된 Job에 반영합니다. */
@Service
public class LinkedJobFailureService implements LinkedJobFailure {

    private final JobStore jobs;
    private final Clock clock;

    public LinkedJobFailureService(JobStore jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void fail(Long jobId, String code, String message) {
        OperationJob job = findLinkedJobForUpdate(jobId);
        if (isRunning(job)) {
            job.fail(now(), code, message);
        }
    }

    @Override
    @Transactional
    public void timeout(Long jobId, String code, String message) {
        OperationJob job = findLinkedJobForUpdate(jobId);
        if (isRunning(job)) {
            job.timeout(now(), code, message);
        }
    }

    private OperationJob findLinkedJobForUpdate(Long jobId) {
        if (jobId == null) {
            return null;
        }

        return jobs.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "연결된 Job을 찾을 수 없습니다. jobId=" + jobId));
    }

    private boolean isRunning(OperationJob job) {
        return job != null && job.getStatus() == JobStatus.RUNNING;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
