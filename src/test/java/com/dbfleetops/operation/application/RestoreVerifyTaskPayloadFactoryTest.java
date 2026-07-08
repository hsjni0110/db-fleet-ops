package com.dbfleetops.operation.application;

import com.dbfleetops.operation.dto.MysqlBackupTaskPayload;
import com.dbfleetops.operation.dto.MysqlRestoreVerifyTaskPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestoreVerifyTaskPayloadFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestoreVerifyTaskPayloadFactory factory =
            new RestoreVerifyTaskPayloadFactory(objectMapper);

    @Test
    void createRestoreVerifyTaskPayloadJson() throws Exception {
        String backupTaskParametersJson = """
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
                """;

        String backupTaskResultPayloadJson = """
                {
                  "status": "VERIFIED",
                  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
                  "fileSizeBytes": 12345,
                  "checksumSha256": "abc123",
                  "createdAt": "2026-07-06T18:10:00+09:00",
                  "message": "backup artifact verified"
                }
                """;

        String restoreVerifyPayloadJson = factory.createRestoreVerifyTaskPayloadJson(100L, 200L,
                backupTaskParametersJson, backupTaskResultPayloadJson);

        MysqlRestoreVerifyTaskPayload payload = objectMapper.readValue(restoreVerifyPayloadJson,
                MysqlRestoreVerifyTaskPayload.class);

        assertThat(payload.operationJobId()).isEqualTo(100L);

        assertThat(payload.databaseId()).isEqualTo(1L);

        assertThat(payload.backupTaskId()).isEqualTo(200L);

        assertThat(payload.sourceDatabaseName()).isEqualTo("orders");

        assertThat(payload.backupFile()).isEqualTo("/tmp/db-fleetops-backups/orders.sql");

        assertThat(payload.host()).isEqualTo("127.0.0.1");

        assertThat(payload.port()).isEqualTo(3306);

        assertThat(payload.username()).isEqualTo("backup_user");

        assertThat(payload.password()).isEqualTo("secret");

        assertThat(payload.temporaryDatabaseName()).startsWith("restore_verify_orders_100_");

        assertThat(payload.expectedTables()).containsExactly("orders", "order_items");

        assertThat(payload.verifyRowCount()).isTrue();

        assertThat(payload.cleanup()).isTrue();
    }

    @Test
    void backupPayloadUsesDefaultOptions() throws Exception {
        String backupTaskParametersJson = """
                {
                  "operationJobId": 100,
                  "databaseId": 1,
                  "databaseName": "orders",
                  "host": "127.0.0.1",
                  "port": 3306,
                  "username": "backup_user",
                  "password": "secret"
                }
                """;

        MysqlBackupTaskPayload payload =
                objectMapper.readValue(backupTaskParametersJson, MysqlBackupTaskPayload.class);

        assertThat(payload.shouldVerifyAfterBackup()).isTrue();

        assertThat(payload.shouldVerifyRowCount()).isTrue();

        assertThat(payload.shouldCleanup()).isTrue();
    }

    @Test
    void sanitizeDatabaseNameForTemporaryDatabaseName() throws Exception {
        String backupTaskParametersJson = """
                {
                  "operationJobId": 100,
                  "databaseId": 1,
                  "databaseName": "orders-prod/2026",
                  "host": "127.0.0.1",
                  "port": 3306,
                  "username": "backup_user",
                  "password": "secret",
                  "verifyAfterBackup": true
                }
                """;

        String backupTaskResultPayloadJson = """
                {
                  "status": "VERIFIED",
                  "backupFile": "/tmp/db-fleetops-backups/orders.sql",
                  "fileSizeBytes": 12345,
                  "checksumSha256": "abc123",
                  "createdAt": "2026-07-06T18:10:00+09:00",
                  "message": "backup artifact verified"
                }
                """;

        String restoreVerifyPayloadJson = factory.createRestoreVerifyTaskPayloadJson(100L, 200L,
                backupTaskParametersJson, backupTaskResultPayloadJson);

        MysqlRestoreVerifyTaskPayload payload = objectMapper.readValue(restoreVerifyPayloadJson,
                MysqlRestoreVerifyTaskPayload.class);

        assertThat(payload.temporaryDatabaseName())
                .startsWith("restore_verify_orders_prod_2026_100_");
    }
}
