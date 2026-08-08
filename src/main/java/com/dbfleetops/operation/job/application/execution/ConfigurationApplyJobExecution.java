package com.dbfleetops.operation.job.application.execution;

import com.dbfleetops.operation.job.application.service.ConfigurationCommandFactory;
import com.dbfleetops.operation.job.application.service.ConfigurationResultMessageFactory;
import com.dbfleetops.operation.job.application.required.ConfigurationApplyOutcome;
import com.dbfleetops.operation.job.application.required.ConfigurationChange;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import org.springframework.stereotype.Component;

/** 설정 변경 Job을 실행하고 적용 결과를 Job 실행 결과로 바꿉니다. */
@Component
public class ConfigurationApplyJobExecution implements JobExecution {

    private final ConfigurationChange changes;
    private final ConfigurationCommandFactory commands;
    private final ConfigurationResultMessageFactory messages;

    public ConfigurationApplyJobExecution(ConfigurationChange changes,
            ConfigurationCommandFactory commands, ConfigurationResultMessageFactory messages) {
        this.changes = changes;
        this.commands = commands;
        this.messages = messages;
    }

    @Override
    public boolean supports(JobType jobType) {
        return jobType == JobType.CONFIGURATION_APPLY;
    }

    @Override
    public JobExecutionOutcome execute(OperationJob job) {
        try {
            ConfigurationApplyOutcome outcome = changes.apply(commands.change(job));
            String message = messages.configurationApply(outcome);

            if (outcome.succeeded()) {
                return JobExecutionOutcome.succeeded(message);
            }

            return JobExecutionOutcome.failed("CONFIGURATION_APPLY_FAILED", message, false);
        } catch (Exception exception) {
            return JobExecutionOutcome.failed(exception.getClass().getSimpleName(),
                    messages.failure(exception, "Configuration apply failed."), false);
        }
    }
}
