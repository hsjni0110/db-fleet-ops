package com.dbfleetops.worker.application;

import com.dbfleetops.operation.application.OperationWorkerService;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "db-fleetops.worker", name = "enabled", havingValue = "true")
public class OperationJobClaimScheduler {

    private static final Logger log = LoggerFactory.getLogger(OperationJobClaimScheduler.class);

    private final OperationWorkerService workerService;
    private final WorkerShutdownState workerShutdownState;
    private final WorkerProperties workerProperties;

    public OperationJobClaimScheduler(
            OperationWorkerService workerService,
            WorkerShutdownState workerShutdownState,
            WorkerProperties workerProperties
    ) {
        this.workerService = workerService;
        this.workerShutdownState = workerShutdownState;
        this.workerProperties = workerProperties;
    }

    @Scheduled(fixedDelayString = "${db-fleetops.worker.claim-interval-ms:5000}")
    public void claimAvailableJob() {
        if (workerShutdownState.isShuttingDown()) {
            log.debug("job_claim_poll_skipped reason=worker_shutdown workerId={}",
                    workerProperties.getId());
            return;
        }

        try {
            ClaimJobResponse response = workerService.claimJob(workerProperties.getId());

            if (!response.claimed()) {
                log.debug("job_claim_poll_empty workerId={}", workerProperties.getId());
                return;
            }

            log.info("job_claim_poll_claimed workerId={} jobId={} jobType={} status={}",
                    workerProperties.getId(), response.jobId(), response.jobType(),
                    response.status());
        } catch (Exception exception) {
            log.error("job_claim_poll_failed workerId={} error={}", workerProperties.getId(),
                    exception.getMessage(), exception);
        }
    }
}
