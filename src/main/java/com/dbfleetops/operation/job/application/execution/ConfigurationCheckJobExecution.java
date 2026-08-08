package com.dbfleetops.operation.job.application.execution;

import com.dbfleetops.operation.job.application.service.ConfigurationCommandFactory;
import com.dbfleetops.operation.job.application.service.ConfigurationResultMessageFactory;
import com.dbfleetops.operation.job.application.required.ConfigurationCheck;
import com.dbfleetops.operation.job.application.required.ConfigurationCheckOutcome;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import org.springframework.stereotype.Component;

/** 설정 점검 Job을 실행하고 점검 결과를 Job 실행 결과로 바꿉니다. */
@Component
public class ConfigurationCheckJobExecution implements JobExecution {

    private final ConfigurationCheck checks;
    private final ConfigurationCommandFactory commands;
    private final ConfigurationResultMessageFactory messages;

    public ConfigurationCheckJobExecution(ConfigurationCheck checks,
            ConfigurationCommandFactory commands, ConfigurationResultMessageFactory messages) {
        this.checks = checks;
        this.commands = commands;
        this.messages = messages;
    }

    @Override
    public boolean supports(JobType jobType) {
        return jobType == JobType.CONFIGURATION_CHECK;
    }

    @Override
    public JobExecutionOutcome execute(OperationJob job) {
        try {
            ConfigurationCheckOutcome outcome = checks.check(commands.check(job));
            return JobExecutionOutcome.succeeded(messages.configurationCheck(outcome));
        } catch (Exception exception) {
            return JobExecutionOutcome.failed(exception.getClass().getSimpleName(),
                    messages.failure(exception, "Configuration check failed."), true);
        }
    }
}
