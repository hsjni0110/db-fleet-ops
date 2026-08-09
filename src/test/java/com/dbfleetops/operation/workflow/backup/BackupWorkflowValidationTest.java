package com.dbfleetops.operation.workflow.backup;

import com.dbfleetops.operation.shared.application.required.AgentReader;
import com.dbfleetops.operation.shared.application.required.CredentialReader;
import com.dbfleetops.operation.shared.application.required.DatabaseReader;
import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import com.dbfleetops.operation.workflow.application.required.BackupPayloadBuilder;
import com.dbfleetops.operation.workflow.application.required.BackupVerificationWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BackupWorkflowValidationTest {

    private final AgentReader agents = mock(AgentReader.class);
    private final DatabaseReader databases = mock(DatabaseReader.class);
    private final CredentialReader credentials = mock(CredentialReader.class);
    private final TaskStore tasks = mock(TaskStore.class);
    private final JobStore jobs = mock(JobStore.class);
    private final BackupPayloadBuilder payloads = mock(BackupPayloadBuilder.class);
    private final BackupVerificationWriter verifications = mock(BackupVerificationWriter.class);

    private BackupWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new BackupWorkflow(agents, databases, credentials, jobs, tasks, payloads,
                verifications, Clock.systemUTC());
    }

    @Test
    void backupTaskRequiresJobId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> workflow.startBackup(null, 1L))
                .withMessage("Operation Job ID는 필수입니다.");

        verifyNoInteractions(tasks, databases, agents, credentials);
    }

    @Test
    void backupTaskRequiresDatabaseId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> workflow.startBackup(1L, null))
                .withMessage("Database ID는 필수입니다.");

        verifyNoInteractions(tasks, databases, agents, credentials);
    }

    @Test
    void taskResultRequiresSupportedTaskType() {
        OperationTask task = OperationTask.create(1L,
                OperationTaskType.COLLECT_LINUX_STATUS, "{}");
        ReflectionTestUtils.setField(task, "id", 1L);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));

        assertThatIllegalStateException()
                .isThrownBy(() -> workflow.continueAfterSuccess(1L, "{}"))
                .withMessageContaining("지원하지 않는 Task 종류입니다.");

        verifyNoInteractions(jobs, payloads, verifications);
    }
}
