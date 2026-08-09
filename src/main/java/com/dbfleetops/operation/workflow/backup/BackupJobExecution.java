package com.dbfleetops.operation.workflow.backup;

import com.dbfleetops.operation.job.application.execution.JobExecution;
import com.dbfleetops.operation.job.application.execution.JobExecutionOutcome;
import com.dbfleetops.operation.workflow.application.provided.BackupStarter;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import org.springframework.stereotype.Component;

/** 백업 Job을 Agent가 실행할 백업 Task로 바꿉니다. */
@Component
public class BackupJobExecution implements JobExecution {

    private final BackupStarter backupStarter;

    public BackupJobExecution(BackupStarter backupStarter) {
        this.backupStarter = backupStarter;
    }

    @Override
    public boolean supports(JobType jobType) {
        return jobType == JobType.BACKUP;
    }

    @Override
    public JobExecutionOutcome execute(OperationJob job) {
        backupStarter.startBackup(job.getId(), job.getTargetDatabaseId());
        return JobExecutionOutcome.inProgress("Backup operation task created.");
    }
}
