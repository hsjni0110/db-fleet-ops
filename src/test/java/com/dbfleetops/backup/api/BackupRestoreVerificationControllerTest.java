package com.dbfleetops.backup.api;

import com.dbfleetops.backup.application.BackupRestoreVerificationQueryService;
import com.dbfleetops.backup.domain.BackupRestoreVerificationStatus;
import com.dbfleetops.backup.dto.BackupRestoreVerificationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupRestoreVerificationControllerTest {

    private final BackupRestoreVerificationQueryService queryService =
            mock(BackupRestoreVerificationQueryService.class);

    private final BackupRestoreVerificationController controller =
            new BackupRestoreVerificationController(queryService);

    @Test
    void getRestoreVerificationByOperationJobId() {
        when(queryService.getByOperationJobId(100L)).thenReturn(response());

        BackupRestoreVerificationResponse response = controller.getByOperationJobId(100L);

        assertThat(response.operationJobId()).isEqualTo(100L);

        assertThat(response.status()).isEqualTo(BackupRestoreVerificationStatus.VERIFIED);
    }

    @Test
    void getLatestRestoreVerificationByDatabaseId() {
        when(queryService.getLatestByDatabaseId(1L)).thenReturn(response());

        BackupRestoreVerificationResponse response = controller.getLatestByDatabaseId(1L);

        assertThat(response.databaseId()).isEqualTo(1L);

        assertThat(response.status()).isEqualTo(BackupRestoreVerificationStatus.VERIFIED);
    }

    @Test
    void getRestoreVerificationById() {
        when(queryService.getById(400L)).thenReturn(response());

        BackupRestoreVerificationResponse response = controller.getById(400L);

        assertThat(response.id()).isEqualTo(400L);

        assertThat(response.status()).isEqualTo(BackupRestoreVerificationStatus.VERIFIED);
    }

    private BackupRestoreVerificationResponse response() {
        return new BackupRestoreVerificationResponse(400L, 100L, 200L, 300L, 1L, "orders",
                "/tmp/db-fleetops-backups/orders.sql", "restore_verify_orders_100",
                BackupRestoreVerificationStatus.VERIFIED, 2, 2, 38512L, null, null,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now(),
                LocalDateTime.now().minusMinutes(2), List.of());
    }
}
