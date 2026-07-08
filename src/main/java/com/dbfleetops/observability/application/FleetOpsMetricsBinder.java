package com.dbfleetops.observability.application;

import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.backup.domain.BackupRestoreVerificationStatus;
import com.dbfleetops.backup.infra.BackupRestoreVerificationRepository;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.infra.OperationJobRepository;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class FleetOpsMetricsBinder {

    private final MeterRegistry meterRegistry;
    private final OperationJobRepository operationJobRepository;
    private final OperationTaskRepository operationTaskRepository;
    private final AgentRepository agentRepository;
    private final BackupRestoreVerificationRepository backupRestoreVerificationRepository;

    public FleetOpsMetricsBinder(MeterRegistry meterRegistry,
            OperationJobRepository operationJobRepository,
            OperationTaskRepository operationTaskRepository, AgentRepository agentRepository,
            BackupRestoreVerificationRepository backupRestoreVerificationRepository) {
        this.meterRegistry = meterRegistry;
        this.operationJobRepository = operationJobRepository;
        this.operationTaskRepository = operationTaskRepository;
        this.agentRepository = agentRepository;
        this.backupRestoreVerificationRepository = backupRestoreVerificationRepository;
    }

    @PostConstruct
    public void bindMetrics() {
        bindOperationJobMetrics();
        bindOperationTaskMetrics();
        bindAgentMetrics();
        bindRestoreVerificationMetrics();
    }

    private void bindOperationJobMetrics() {
        for (JobStatus status : JobStatus.values()) {
            Gauge.builder("dbfleetops_operation_jobs", operationJobRepository,
                    repository -> repository.countByStatus(status))
                    .description("Current number of operation jobs by status.")
                    .tag("status", status.name()).register(meterRegistry);
        }
    }

    private void bindOperationTaskMetrics() {
        for (OperationTaskStatus status : OperationTaskStatus.values()) {
            Gauge.builder("dbfleetops_operation_tasks", operationTaskRepository,
                    repository -> repository.countByStatus(status))
                    .description("Current number of operation tasks by status.")
                    .tag("status", status.name()).register(meterRegistry);
        }
    }

    private void bindAgentMetrics() {
        for (AgentStatus status : AgentStatus.values()) {
            Gauge.builder("dbfleetops_agents", agentRepository,
                    repository -> repository.countByStatus(status))
                    .description("Current number of agents by status.").tag("status", status.name())
                    .register(meterRegistry);
        }
    }

    private void bindRestoreVerificationMetrics() {
        for (BackupRestoreVerificationStatus status : BackupRestoreVerificationStatus.values()) {
            Gauge.builder("dbfleetops_restore_verifications", backupRestoreVerificationRepository,
                    repository -> repository.countByStatus(status))
                    .description("Current number of restore verification records by status.")
                    .tag("status", status.name()).register(meterRegistry);
        }
    }
}
