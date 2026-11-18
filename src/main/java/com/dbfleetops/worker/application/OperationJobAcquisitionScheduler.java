package com.dbfleetops.worker.application;

import com.dbfleetops.operation.job.application.provided.JobAcquisition;
import com.dbfleetops.operation.job.dto.JobAcquisitionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "db-fleetops.worker", name = "enabled", havingValue = "true")
public class OperationJobAcquisitionScheduler {

    private static final Logger log = LoggerFactory.getLogger(OperationJobAcquisitionScheduler.class);

    private final JobAcquisition jobAcquisition;
    private final WorkerShutdownState workerShutdownState;
    private final WorkerProperties workerProperties;

    public OperationJobAcquisitionScheduler(
            JobAcquisition jobAcquisition,
            WorkerShutdownState workerShutdownState,
            WorkerProperties workerProperties
    ) {
        this.jobAcquisition = jobAcquisition;
        this.workerShutdownState = workerShutdownState;
        this.workerProperties = workerProperties;
    }

    @Scheduled(fixedDelayString = "${db-fleetops.worker.acquisition-interval-ms:5000}")
    public void acquireAvailableJob() {
        if (workerShutdownState.isShuttingDown()) {
            log.debug("job_acquisition_poll_skipped reason=worker_shutdown workerId={}",
                    workerProperties.getId());
            return;
        }

        try {
            JobAcquisitionResponse response = jobAcquisition.acquireJob(workerProperties.getId());

            if (!response.acquired()) {
                log.debug("job_acquisition_poll_empty workerId={}", workerProperties.getId());
                return;
            }

            log.info("job_acquisition_poll_acquired workerId={} jobId={} jobType={} status={}",
                    workerProperties.getId(), response.jobId(), response.jobType(),
                    response.status());
        } catch (Exception exception) {
            log.error("job_acquisition_poll_failed workerId={} error={}", workerProperties.getId(),
                    exception.getMessage(), exception);
        }
    }
}
