package com.dbfleetops.operation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationTaskTypeTest {

    @Test
    void mysqlRestoreVerifyTaskTypeExists() {
        OperationTaskType taskType = OperationTaskType.valueOf("MYSQL_RESTORE_VERIFY");

        assertThat(taskType).isEqualTo(OperationTaskType.MYSQL_RESTORE_VERIFY);
    }

    @Test
    void operationTaskTypesContainBackupAndRestoreVerify() {
        assertThat(OperationTaskType.values()).contains(OperationTaskType.MYSQL_LOGICAL_BACKUP,
                OperationTaskType.MYSQL_RESTORE_VERIFY);
    }
}
