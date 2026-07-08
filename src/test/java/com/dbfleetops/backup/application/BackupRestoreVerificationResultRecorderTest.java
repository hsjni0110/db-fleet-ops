package com.dbfleetops.backup.application;

import com.dbfleetops.backup.domain.BackupRestoreVerification;
import com.dbfleetops.backup.domain.BackupRestoreVerificationItem;
import com.dbfleetops.backup.domain.BackupRestoreVerificationStatus;
import com.dbfleetops.backup.infra.BackupRestoreVerificationItemRepository;
import com.dbfleetops.backup.infra.BackupRestoreVerificationRepository;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.dto.MysqlRestoreVerifyTaskResultPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class BackupRestoreVerificationResultRecorderTest {

    private final BackupRestoreVerificationRepository verificationRepository =
            mock(BackupRestoreVerificationRepository.class);

    private final BackupRestoreVerificationItemRepository itemRepository =
            mock(BackupRestoreVerificationItemRepository.class);

    private final BackupRestoreVerificationResultRecorder recorder =
            new BackupRestoreVerificationResultRecorder(verificationRepository, itemRepository,
                    new ObjectMapper());

    @Test
    void recordVerifiedRestoreVerificationResult() {
        OperationTask restoreVerifyTask =
                OperationTask.createForJob(1L, 100L, OperationTaskType.MYSQL_RESTORE_VERIFY, "{}");

        setId(restoreVerifyTask, 300L);

        when(verificationRepository.save(any(BackupRestoreVerification.class)))
                .thenAnswer(invocation -> {
                    BackupRestoreVerification verification = invocation.getArgument(0);

                    setId(verification, 400L);

                    return verification;
                });

        MysqlRestoreVerifyTaskResultPayload result = recorder.record(restoreVerifyTask, """
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
                  "totalRowCount": 38512,
                  "items": [
                    {
                      "tableName": "orders",
                      "existsInRestoredDb": true,
                      "rowCount": 12000,
                      "status": "VERIFIED",
                      "message": "table verified"
                    },
                    {
                      "tableName": "order_items",
                      "existsInRestoredDb": true,
                      "rowCount": 26512,
                      "status": "VERIFIED",
                      "message": "table verified"
                    }
                  ],
                  "message": "restore verification completed"
                }
                """);

        assertThat(result.status()).isEqualTo("VERIFIED");

        ArgumentCaptor<BackupRestoreVerification> verificationCaptor =
                ArgumentCaptor.forClass(BackupRestoreVerification.class);

        verify(verificationRepository).save(verificationCaptor.capture());

        BackupRestoreVerification savedVerification = verificationCaptor.getValue();

        assertThat(savedVerification.getStatus())
                .isEqualTo(BackupRestoreVerificationStatus.VERIFIED);

        assertThat(savedVerification.getRestoredTableCount()).isEqualTo(2);

        assertThat(savedVerification.getCheckedTableCount()).isEqualTo(2);

        assertThat(savedVerification.getTotalRowCount()).isEqualTo(38512L);

        verify(itemRepository, times(2)).save(any(BackupRestoreVerificationItem.class));
    }

    @Test
    void recordFailedRestoreVerificationResult() {
        OperationTask restoreVerifyTask =
                OperationTask.createForJob(1L, 100L, OperationTaskType.MYSQL_RESTORE_VERIFY, "{}");

        setId(restoreVerifyTask, 300L);

        when(verificationRepository.save(any(BackupRestoreVerification.class)))
                .thenAnswer(invocation -> {
                    BackupRestoreVerification verification = invocation.getArgument(0);

                    setId(verification, 400L);

                    return verification;
                });

        MysqlRestoreVerifyTaskResultPayload result = recorder.record(restoreVerifyTask, """
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
                  "totalRowCount": 12000,
                  "items": [
                    {
                      "tableName": "order_items",
                      "existsInRestoredDb": false,
                      "status": "MISSING",
                      "message": "expected table is missing in restored database"
                    }
                  ],
                  "message": "restore verification failed",
                  "errorCode": "RESTORE_VERIFY_FAILED",
                  "errorMessage": "one or more restored table checks failed"
                }
                """);

        assertThat(result.status()).isEqualTo("FAILED");

        ArgumentCaptor<BackupRestoreVerification> verificationCaptor =
                ArgumentCaptor.forClass(BackupRestoreVerification.class);

        verify(verificationRepository).save(verificationCaptor.capture());

        BackupRestoreVerification savedVerification = verificationCaptor.getValue();

        assertThat(savedVerification.getStatus()).isEqualTo(BackupRestoreVerificationStatus.FAILED);

        assertThat(savedVerification.getErrorCode()).isEqualTo("RESTORE_VERIFY_FAILED");
    }

    private void setId(Object target, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(target, "id", id);
    }
}
