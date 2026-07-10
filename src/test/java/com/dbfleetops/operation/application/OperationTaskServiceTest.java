package com.dbfleetops.operation.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentHostMetric;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.infra.AgentHostMetricRepository;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.backup.application.BackupRestoreVerificationResultRecorder;
import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.dto.CompleteOperationTaskRequest;
import com.dbfleetops.operation.dto.CreateOperationTaskRequest;
import com.dbfleetops.operation.dto.FailOperationTaskRequest;
import com.dbfleetops.operation.dto.MysqlRestoreVerifyTaskResultPayload;
import com.dbfleetops.operation.dto.NextOperationTaskResponse;
import com.dbfleetops.operation.dto.OperationTaskResponse;
import com.dbfleetops.operation.dto.StartOperationTaskRequest;
import com.dbfleetops.operation.infra.OperationJobRepository;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class OperationTaskServiceTest {

        @Mock
        private AgentRepository agentRepository;

        @Mock
        private OperationTaskRepository taskRepository;

        @Mock
        private OperationJobRepository jobRepository;

        @Mock
        private ManagedDatabaseRepository databaseRepository;

        @Mock
        private DatabaseCredentialRepository credentialRepository;

        @Mock
        private AgentHostMetricRepository agentHostMetricRepository;

        @Mock
        private BackupRestoreVerificationResultRecorder backupRestoreVerificationResultRecorder;

        private OperationTaskService newService() {
                RestoreVerifyTaskPayloadFactory restoreVerifyTaskPayloadFactory =
                                new RestoreVerifyTaskPayloadFactory(new ObjectMapper());

                return new OperationTaskService(agentRepository, taskRepository, jobRepository,
                                databaseRepository, credentialRepository, agentHostMetricRepository,
                                restoreVerifyTaskPayloadFactory, backupRestoreVerificationResultRecorder);
        }

        @Test
        void createTaskCreatesQueuedTask() {
                Agent agent = newAgent();

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.save(any(OperationTask.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                OperationTaskService service = newService();

                var response = service.createTask(new CreateOperationTaskRequest(1L, null,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}"));

                assertThat(response.taskType()).isEqualTo(OperationTaskType.COLLECT_LINUX_STATUS);

                assertThat(response.status()).isEqualTo(OperationTaskStatus.QUEUED);
        }

        @Test
        void nextTaskReturnsQueuedTask() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(1L,
                                OperationTaskStatus.QUEUED)).thenReturn(List.of(task));

                OperationTaskService service = newService();

                NextOperationTaskResponse response = service.nextTask(1L, "agent-token-001");

                assertThat(response.hasTask()).isTrue();

                assertThat(response.taskType()).isEqualTo(OperationTaskType.COLLECT_LINUX_STATUS);
        }

        @Test
        void startTaskChangesQueuedTaskToRunning() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                OperationTaskService service = newService();

                var response = service.startTask(1L, 10L,
                                new StartOperationTaskRequest("agent-token-001"));

                assertThat(response.status()).isEqualTo(OperationTaskStatus.RUNNING);
        }

        @Test
        void completeTaskChangesRunningTaskToSucceeded() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                task.start();

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                OperationTaskService service = newService();

                var response = service.completeTask(1L, 10L, new CompleteOperationTaskRequest(
                                "agent-token-001", "{\"cpuUsagePercent\":12.5}"));

                assertThat(response.status()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(response.resultPayloadJson()).isEqualTo("{\"cpuUsagePercent\":12.5}");
        }

        @Test
        void failTaskChangesRunningTaskToFailed() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                task.start();

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                OperationTaskService service = newService();

                var response = service.failTask(1L, 10L,
                                new FailOperationTaskRequest("agent-token-001",
                                                "LINUX_STATUS_FAILED",
                                                "failed to read /proc/stat"));

                assertThat(response.status()).isEqualTo(OperationTaskStatus.FAILED);

                assertThat(response.errorCode()).isEqualTo("LINUX_STATUS_FAILED");
        }

        @Test
        void nextTaskThrowsExceptionWhenTokenIsInvalid() {
                Agent agent = newAgent();

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                OperationTaskService service = newService();

                assertThrows(IllegalArgumentException.class,
                                () -> service.nextTask(1L, "wrong-token"));
        }

        @Test
        void createBackupTaskForOperationJobCreatesMySQLBackupTaskOnOnlineAgent() {
                Agent agent = newAgent();

                when(agentRepository
                                .findFirstByStatusOrderByLastHeartbeatAtDesc(AgentStatus.ONLINE))
                                                .thenReturn(Optional.of(agent));

                when(databaseRepository.findById(1L)).thenReturn(Optional.of(newManagedDatabase()));

                when(credentialRepository.findByDatabaseId(1L))
                                .thenReturn(Optional.of(new DatabaseCredential(1L, "root", "rootpw")));

                when(taskRepository.save(any(OperationTask.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                OperationTaskService service = newService();

                OperationTaskResponse response = service.createBackupTaskForOperationJob(100L, 1L);

                assertThat(response.operationJobId()).isEqualTo(100L);

                assertThat(response.taskType()).isEqualTo(OperationTaskType.MYSQL_LOGICAL_BACKUP);

                assertThat(response.status()).isEqualTo(OperationTaskStatus.QUEUED);

                assertThat(response.parametersJson()).contains("\"operationJobId\": 100",
                                "\"databaseId\": 1", "\"databaseName\": \"orders\"",
                                "\"host\": \"target-mysql\"", "\"port\": 3306",
                                "\"username\": \"root\"", "\"password\": \"rootpw\"",
                                "\"backupType\": \"LOGICAL\"", "\"verifyAfterBackup\": false");
        }

        @Test
        void createBackupTaskForOperationJobThrowsExceptionWhenNoOnlineAgentExists() {
                when(agentRepository
                                .findFirstByStatusOrderByLastHeartbeatAtDesc(AgentStatus.ONLINE))
                                                .thenReturn(Optional.empty());

                OperationTaskService service = newService();

                assertThrows(IllegalStateException.class,
                                () -> service.createBackupTaskForOperationJob(100L, 1L));
        }

        @Test
        void createBackupTaskForOperationJobThrowsExceptionWhenDatabaseDoesNotExist() {
                Agent agent = newAgent();

                when(agentRepository
                                .findFirstByStatusOrderByLastHeartbeatAtDesc(AgentStatus.ONLINE))
                                                .thenReturn(Optional.of(agent));

                when(databaseRepository.findById(1L)).thenReturn(Optional.empty());

                OperationTaskService service = newService();

                assertThrows(IllegalArgumentException.class,
                                () -> service.createBackupTaskForOperationJob(100L, 1L));
        }

        @Test
        void createBackupTaskForOperationJobThrowsExceptionWhenCredentialDoesNotExist() {
                Agent agent = newAgent();

                when(agentRepository
                                .findFirstByStatusOrderByLastHeartbeatAtDesc(AgentStatus.ONLINE))
                                                .thenReturn(Optional.of(agent));

                when(databaseRepository.findById(1L)).thenReturn(Optional.of(newManagedDatabase()));

                when(credentialRepository.findByDatabaseId(1L)).thenReturn(Optional.empty());

                OperationTaskService service = newService();

                assertThrows(IllegalArgumentException.class,
                                () -> service.createBackupTaskForOperationJob(100L, 1L));
        }

        @Test
        void completeTaskChangesLinkedOperationJobToSucceeded() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.createForJob(1L, 100L,
                                OperationTaskType.MYSQL_LOGICAL_BACKUP, """
                                                {
                                                  "operationJobId": 100,
                                                  "databaseId": 1,
                                                  "backupType": "LOGICAL",
                                                  "compression": true,
                                                  "verifyAfterBackup": false
                                                }
                                                """);

                task.start();

                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", java.time.LocalDateTime.now().plusSeconds(60));

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                when(jobRepository.findById(100L)).thenReturn(Optional.of(job));

                OperationTaskService service = newService();

                service.completeTask(1L, 10L, new CompleteOperationTaskRequest("agent-token-001",
                                "{\"status\":\"CREATED\"}"));

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);

                assertThat(job.getResultCode()).isEqualTo("SUCCESS");

                assertThat(job.getResultMessage()).isEqualTo("{\"status\":\"CREATED\"}");
        }

        @Test
        void completeBackupTaskCreatesRestoreVerifyTaskWhenVerifyAfterBackupTrue() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.createForJob(1L, 100L,
                                OperationTaskType.MYSQL_LOGICAL_BACKUP, """
                                                {
                                                  "operationJobId": 100,
                                                  "databaseId": 1,
                                                  "databaseName": "orders",
                                                  "host": "127.0.0.1",
                                                  "port": 3306,
                                                  "username": "backup_user",
                                                  "password": "secret",
                                                  "backupType": "LOGICAL",
                                                  "compression": false,
                                                  "verifyAfterBackup": true,
                                                  "expectedTables": ["orders", "order_items"],
                                                  "verifyRowCount": true,
                                                  "cleanup": true
                                                }
                                                """);

                task.start();

                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", java.time.LocalDateTime.now().plusSeconds(60));

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                when(jobRepository.findById(100L)).thenReturn(Optional.of(job));

                when(taskRepository.save(any(OperationTask.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                OperationTaskService service = newService();

                service.completeTask(1L, 10L, new CompleteOperationTaskRequest("agent-token-001",
                                """
                                                {
                                                  "status": "VERIFIED",
                                                  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
                                                  "fileSizeBytes": 12345,
                                                  "checksumSha256": "abc123",
                                                  "createdAt": "2026-07-06T18:10:00+09:00",
                                                  "message": "backup artifact verified"
                                                }
                                                """));

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);

                verify(taskRepository).save(any(OperationTask.class));
        }

        @Test
        void failTaskChangesLinkedOperationJobToFailed() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.createForJob(1L, 100L,
                                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");

                task.start();

                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", java.time.LocalDateTime.now().plusSeconds(60));

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                when(jobRepository.findById(100L)).thenReturn(Optional.of(job));

                OperationTaskService service = newService();

                service.failTask(1L, 10L, new FailOperationTaskRequest("agent-token-001",
                                "BACKUP_FAILED", "mysqldump failed"));

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.FAILED);

                assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);

                assertThat(job.getResultCode()).isEqualTo("BACKUP_FAILED");

                assertThat(job.getResultMessage()).isEqualTo("mysqldump failed");
        }

        @Test
        void completeTaskDoesNotChangeOperationJobWhenTaskIsNotLinked() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                task.start();

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                OperationTaskService service = newService();

                service.completeTask(1L, 10L, new CompleteOperationTaskRequest("agent-token-001",
                                "{\"cpuUsagePercent\":12.5}"));

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                org.mockito.Mockito.verifyNoInteractions(jobRepository);
        }

        @Test
        void completeLinuxStatusTaskPersistsAgentHostMetric() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                task.start();

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                OperationTaskService service = newService();

                service.completeTask(1L, 10L,
                                new CompleteOperationTaskRequest("agent-token-001", """
                                                {
                                                  "cpuUsagePercent": 12.5,
                                                  "memoryUsagePercent": 61.2,
                                                  "diskUsagePercent": 70.1
                                                }
                                                """));

                verify(agentHostMetricRepository).save(any(AgentHostMetric.class));
        }

        @Test
        void completeRestoreVerifyTaskSucceedsLinkedBackupJobWhenVerified() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.createForJob(1L, 100L,
                                OperationTaskType.MYSQL_RESTORE_VERIFY, "{}");

                task.start();

                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", java.time.LocalDateTime.now().plusSeconds(60));

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                when(jobRepository.findById(100L)).thenReturn(Optional.of(job));

                when(backupRestoreVerificationResultRecorder.record(any(OperationTask.class),
                                any())).thenReturn(new MysqlRestoreVerifyTaskResultPayload(
                                                "VERIFIED", 100L, 1L, 200L, "orders",
                                                "/tmp/orders.sql", "restore_verify_orders_100", 2,
                                                2, 10L, "2026-07-08T13:00:00+09:00",
                                                "2026-07-08T13:01:00+09:00", List.of(),
                                                "restore verification completed", null, null));

                OperationTaskService service = newService();

                service.completeTask(1L, 10L, new CompleteOperationTaskRequest("agent-token-001",
                                """
                                                {
                                                  "status": "VERIFIED",
                                                  "operationJobId": 100,
                                                  "databaseId": 1,
                                                  "backupTaskId": 200,
                                                  "sourceDatabaseName": "orders",
                                                  "backupFile": "/tmp/orders.sql",
                                                  "temporaryDatabaseName": "restore_verify_orders_100",
                                                  "restoredTableCount": 2,
                                                  "checkedTableCount": 2,
                                                  "totalRowCount": 10,
                                                  "items": [],
                                                  "message": "restore verification completed"
                                                }
                                                """));

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);

                assertThat(job.getResultCode()).isEqualTo("SUCCESS");
        }

        @Test
        void completeRestoreVerifyTaskFailsLinkedBackupJobWhenRestoreVerifyFailed() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.createForJob(1L, 100L,
                                OperationTaskType.MYSQL_RESTORE_VERIFY, "{}");

                task.start();

                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", java.time.LocalDateTime.now().plusSeconds(60));

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                when(jobRepository.findById(100L)).thenReturn(Optional.of(job));

                when(backupRestoreVerificationResultRecorder.record(any(OperationTask.class),
                                any())).thenReturn(new MysqlRestoreVerifyTaskResultPayload("FAILED",
                                                100L, 1L, 200L, "orders", "/tmp/orders.sql",
                                                "restore_verify_orders_100", 1, 1, 0L,
                                                "2026-07-08T13:00:00+09:00",
                                                "2026-07-08T13:01:00+09:00", List.of(),
                                                "restore verification failed",
                                                "RESTORE_VERIFY_FAILED",
                                                "expected table is missing"));

                OperationTaskService service = newService();

                service.completeTask(1L, 10L, new CompleteOperationTaskRequest("agent-token-001",
                                """
                                                {
                                                  "status": "FAILED",
                                                  "operationJobId": 100,
                                                  "databaseId": 1,
                                                  "backupTaskId": 200,
                                                  "sourceDatabaseName": "orders",
                                                  "backupFile": "/tmp/orders.sql",
                                                  "temporaryDatabaseName": "restore_verify_orders_100",
                                                  "restoredTableCount": 1,
                                                  "checkedTableCount": 1,
                                                  "totalRowCount": 0,
                                                  "items": [],
                                                  "message": "restore verification failed",
                                                  "errorCode": "RESTORE_VERIFY_FAILED",
                                                  "errorMessage": "expected table is missing"
                                                }
                                                """));

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);

                assertThat(job.getResultCode()).isEqualTo("RESTORE_VERIFY_FAILED");

                assertThat(job.getResultMessage()).isEqualTo("expected table is missing");
        }

        private Agent newAgent() {
                return Agent.register("local-agent", "localhost", "127.0.0.1", "Linux", "0.1.0",
                                "agent-token-001");
        }

        @Test
        void completeBackupTaskSucceedsLinkedJobWhenVerifyAfterBackupFalse() {
                Agent agent = newAgent();

                OperationTask task = OperationTask.createForJob(1L, 100L,
                                OperationTaskType.MYSQL_LOGICAL_BACKUP, """
                                                {
                                                  "operationJobId": 100,
                                                  "databaseId": 1,
                                                  "backupType": "LOGICAL",
                                                  "compression": true,
                                                  "verifyAfterBackup": false
                                                }
                                                """);

                task.start();

                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", java.time.LocalDateTime.now().plusSeconds(60));

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
                when(jobRepository.findById(100L)).thenReturn(Optional.of(job));

                OperationTaskService service = newService();

                service.completeTask(1L, 10L, new CompleteOperationTaskRequest("agent-token-001",
                                """
                                                {
                                                  "status": "VERIFIED",
                                                  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
                                                  "fileSizeBytes": 12345,
                                                  "checksumSha256": "abc123",
                                                  "createdAt": "2026-07-08T14:00:00+09:00",
                                                  "message": "backup artifact verified"
                                                }
                                                """));

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);

                assertThat(job.getResultCode()).isEqualTo("SUCCESS");

                assertThat(job.getResultMessage()).contains("\"status\": \"VERIFIED\"");

                verify(taskRepository, never()).save(any(OperationTask.class));

                verify(backupRestoreVerificationResultRecorder, never())
                                .record(any(OperationTask.class), any());
        }

        @Test
        void completeRestoreVerifyTaskFailsLinkedBackupJobWhenFailed() {
                Agent agent = newAgent();

                OperationTask restoreVerifyTask = OperationTask.createForJob(1L, 100L,
                                OperationTaskType.MYSQL_RESTORE_VERIFY, "{}");

                restoreVerifyTask.start();

                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", java.time.LocalDateTime.now().plusSeconds(60));

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
                when(taskRepository.findById(10L)).thenReturn(Optional.of(restoreVerifyTask));
                when(jobRepository.findById(100L)).thenReturn(Optional.of(job));

                when(backupRestoreVerificationResultRecorder.record(any(OperationTask.class),
                                any())).thenReturn(new MysqlRestoreVerifyTaskResultPayload("FAILED",
                                                100L, 1L, 200L, "orders",
                                                "/tmp/db-fleetops-backups/orders.sql",
                                                "restore_verify_orders_100", 1, 1, 12000L,
                                                "2026-07-08T14:00:00+09:00",
                                                "2026-07-08T14:01:00+09:00", List.of(),
                                                "restore verification failed",
                                                "RESTORE_VERIFY_FAILED",
                                                "one or more restored table checks failed"));

                OperationTaskService service = newService();

                service.completeTask(1L, 10L, new CompleteOperationTaskRequest("agent-token-001",
                                """
                                                {
                                                  "status": "FAILED",
                                                  "operationJobId": 100,
                                                  "databaseId": 1,
                                                  "backupTaskId": 200,
                                                  "sourceDatabaseName": "orders",
                                                  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
                                                  "temporaryDatabaseName": "restore_verify_orders_100",
                                                  "restoredTableCount": 1,
                                                  "checkedTableCount": 1,
                                                  "totalRowCount": 12000,
                                                  "items": [],
                                                  "message": "restore verification failed",
                                                  "errorCode": "RESTORE_VERIFY_FAILED",
                                                  "errorMessage": "one or more restored table checks failed"
                                                }
                                                """));

                assertThat(restoreVerifyTask.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);

                assertThat(job.getResultCode()).isEqualTo("RESTORE_VERIFY_FAILED");

                assertThat(job.getResultMessage())
                                .isEqualTo("one or more restored table checks failed");

                verify(backupRestoreVerificationResultRecorder).record(eq(restoreVerifyTask),
                                any());
        }

        @Test
        void completeRestoreVerifyTaskFailsLinkedBackupJobWhenCleanupFailed() {
                Agent agent = newAgent();

                OperationTask restoreVerifyTask = OperationTask.createForJob(1L, 100L,
                                OperationTaskType.MYSQL_RESTORE_VERIFY, "{}");

                restoreVerifyTask.start();

                OperationJob job =
                                OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

                job.start("worker-1", java.time.LocalDateTime.now().plusSeconds(60));

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
                when(taskRepository.findById(10L)).thenReturn(Optional.of(restoreVerifyTask));
                when(jobRepository.findById(100L)).thenReturn(Optional.of(job));

                when(backupRestoreVerificationResultRecorder.record(any(OperationTask.class),
                                any())).thenReturn(new MysqlRestoreVerifyTaskResultPayload(
                                                "CLEANUP_FAILED", 100L, 1L, 200L, "orders",
                                                "/tmp/db-fleetops-backups/orders.sql",
                                                "restore_verify_orders_100", 2, 2, 38512L,
                                                "2026-07-08T14:00:00+09:00",
                                                "2026-07-08T14:01:00+09:00", List.of(),
                                                "restore verification completed but cleanup failed",
                                                "CLEANUP_FAILED", "drop database failed"));

                OperationTaskService service = newService();

                service.completeTask(1L, 10L, new CompleteOperationTaskRequest("agent-token-001",
                                """
                                                {
                                                  "status": "CLEANUP_FAILED",
                                                  "operationJobId": 100,
                                                  "databaseId": 1,
                                                  "backupTaskId": 200,
                                                  "sourceDatabaseName": "orders",
                                                  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
                                                  "temporaryDatabaseName": "restore_verify_orders_100",
                                                  "restoredTableCount": 2,
                                                  "checkedTableCount": 2,
                                                  "totalRowCount": 38512,
                                                  "items": [],
                                                  "message": "restore verification completed but cleanup failed",
                                                  "errorCode": "CLEANUP_FAILED",
                                                  "errorMessage": "drop database failed"
                                                }
                                                """));

                assertThat(restoreVerifyTask.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);

                assertThat(job.getResultCode()).isEqualTo("CLEANUP_FAILED");

                assertThat(job.getResultMessage()).isEqualTo("drop database failed");
        }

        private ManagedDatabase newManagedDatabase() {
                return new ManagedDatabase("target-mysql", "target-mysql", 3306, "orders",
                                DatabaseEngine.MYSQL, "LOCAL", "target-mysql", "platform-team",
                                "local target database");
        }
}
