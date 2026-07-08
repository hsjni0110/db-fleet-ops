package com.dbfleetops.backup.integration;

import com.dbfleetops.backup.domain.BackupRestoreVerification;
import com.dbfleetops.backup.domain.BackupRestoreVerificationItem;
import com.dbfleetops.backup.domain.BackupRestoreVerificationItemStatus;
import com.dbfleetops.backup.domain.BackupRestoreVerificationStatus;
import com.dbfleetops.backup.infra.BackupRestoreVerificationItemRepository;
import com.dbfleetops.backup.infra.BackupRestoreVerificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BackupRestoreVerificationPersistenceTest {

    @Autowired
    private BackupRestoreVerificationRepository verificationRepository;

    @Autowired
    private BackupRestoreVerificationItemRepository verificationItemRepository;

    @Test
    void saveRestoreVerification() {
        BackupRestoreVerification verification = BackupRestoreVerification.create(100L, 200L, 201L,
                1L, "orders", "/tmp/db-fleetops-backups/orders-20260706-173000.sql",
                "restore_verify_orders_100");

        BackupRestoreVerification saved = verificationRepository.save(verification);

        assertThat(saved.getId()).isNotNull();

        assertThat(saved.getStatus()).isEqualTo(BackupRestoreVerificationStatus.REQUESTED);

        assertThat(saved.getOperationJobId()).isEqualTo(100L);

        assertThat(saved.getBackupTaskId()).isEqualTo(200L);

        assertThat(saved.getRestoreVerifyTaskId()).isEqualTo(201L);

        assertThat(saved.getDatabaseId()).isEqualTo(1L);

        assertThat(saved.getSourceDatabaseName()).isEqualTo("orders");

        assertThat(saved.getTemporaryDatabaseName()).isEqualTo("restore_verify_orders_100");

        assertThat(saved.getRestoredTableCount()).isZero();

        assertThat(saved.getCheckedTableCount()).isZero();

        assertThat(saved.getTotalRowCount()).isZero();
    }

    @Test
    void startAndVerifyRestoreVerification() {
        BackupRestoreVerification verification = BackupRestoreVerification.create(100L, 200L, 201L,
                1L, "orders", "/tmp/db-fleetops-backups/orders.sql", "restore_verify_orders_100");

        BackupRestoreVerification saved = verificationRepository.save(verification);

        saved.start();

        saved.verify(12, 2, 38512L);

        assertThat(saved.getStatus()).isEqualTo(BackupRestoreVerificationStatus.VERIFIED);

        assertThat(saved.getRestoredTableCount()).isEqualTo(12);

        assertThat(saved.getCheckedTableCount()).isEqualTo(2);

        assertThat(saved.getTotalRowCount()).isEqualTo(38512L);

        assertThat(saved.getStartedAt()).isNotNull();

        assertThat(saved.getCompletedAt()).isNotNull();
    }

    @Test
    void failRestoreVerification() {
        BackupRestoreVerification verification = BackupRestoreVerification.create(100L, 200L, 201L,
                1L, "orders", "/tmp/db-fleetops-backups/orders.sql", "restore_verify_orders_100");

        BackupRestoreVerification saved = verificationRepository.save(verification);

        saved.start();

        saved.fail("RESTORE_FAILED", "mysql restore exited with code 1");

        assertThat(saved.getStatus()).isEqualTo(BackupRestoreVerificationStatus.FAILED);

        assertThat(saved.getErrorCode()).isEqualTo("RESTORE_FAILED");

        assertThat(saved.getErrorMessage()).isEqualTo("mysql restore exited with code 1");

        assertThat(saved.getCompletedAt()).isNotNull();
    }

    @Test
    void markCleanupFailed() {
        BackupRestoreVerification verification = BackupRestoreVerification.create(100L, 200L, 201L,
                1L, "orders", "/tmp/db-fleetops-backups/orders.sql", "restore_verify_orders_100");

        BackupRestoreVerification saved = verificationRepository.save(verification);

        saved.start();

        saved.cleanupFailed("CLEANUP_FAILED", "failed to drop temporary database");

        assertThat(saved.getStatus()).isEqualTo(BackupRestoreVerificationStatus.CLEANUP_FAILED);

        assertThat(saved.getErrorCode()).isEqualTo("CLEANUP_FAILED");

        assertThat(saved.getErrorMessage()).isEqualTo("failed to drop temporary database");
    }

    @Test
    void saveRestoreVerificationItems() {
        BackupRestoreVerification verification = BackupRestoreVerification.create(100L, 200L, 201L,
                1L, "orders", "/tmp/db-fleetops-backups/orders.sql", "restore_verify_orders_100");

        BackupRestoreVerification savedVerification = verificationRepository.save(verification);

        BackupRestoreVerificationItem orders =
                BackupRestoreVerificationItem.verified(savedVerification.getId(), "orders", 12000L);

        BackupRestoreVerificationItem orderItems = BackupRestoreVerificationItem
                .verified(savedVerification.getId(), "order_items", 26512L);

        verificationItemRepository.saveAll(List.of(orderItems, orders));

        List<BackupRestoreVerificationItem> items = verificationItemRepository
                .findByVerificationIdOrderByTableNameAsc(savedVerification.getId());

        assertThat(items).hasSize(2);

        assertThat(items).extracting(BackupRestoreVerificationItem::getTableName)
                .containsExactly("order_items", "orders");

        assertThat(items).extracting(BackupRestoreVerificationItem::getStatus)
                .containsOnly(BackupRestoreVerificationItemStatus.VERIFIED);
    }

    @Test
    void saveMissingRestoreVerificationItem() {
        BackupRestoreVerification verification = BackupRestoreVerification.create(100L, 200L, 201L,
                1L, "orders", "/tmp/db-fleetops-backups/orders.sql", "restore_verify_orders_100");

        BackupRestoreVerification savedVerification = verificationRepository.save(verification);

        BackupRestoreVerificationItem missingItem =
                BackupRestoreVerificationItem.missing(savedVerification.getId(), "order_items",
                        "expected table is missing in restored database");

        BackupRestoreVerificationItem savedItem = verificationItemRepository.save(missingItem);

        assertThat(savedItem.getStatus()).isEqualTo(BackupRestoreVerificationItemStatus.MISSING);

        assertThat(savedItem.getExistsInRestoredDb()).isFalse();

        assertThat(savedItem.getRowCount()).isNull();

        assertThat(savedItem.getMessage()).contains("missing");
    }

    @Test
    void findRestoreVerificationByOperationJobId() {
        BackupRestoreVerification verification = BackupRestoreVerification.create(100L, 200L, 201L,
                1L, "orders", "/tmp/db-fleetops-backups/orders.sql", "restore_verify_orders_100");

        verificationRepository.save(verification);

        var result = verificationRepository.findByOperationJobId(100L);

        assertThat(result).isPresent();

        assertThat(result.get().getOperationJobId()).isEqualTo(100L);
    }

    @Test
    void findRestoreVerificationByRestoreVerifyTaskId() {
        BackupRestoreVerification verification = BackupRestoreVerification.create(100L, 200L, 201L,
                1L, "orders", "/tmp/db-fleetops-backups/orders.sql", "restore_verify_orders_100");

        verificationRepository.save(verification);

        var result = verificationRepository.findByRestoreVerifyTaskId(201L);

        assertThat(result).isPresent();

        assertThat(result.get().getRestoreVerifyTaskId()).isEqualTo(201L);
    }

    @Test
    void findItemsByStatus() {
        BackupRestoreVerification verification = BackupRestoreVerification.create(100L, 200L, 201L,
                1L, "orders", "/tmp/db-fleetops-backups/orders.sql", "restore_verify_orders_100");

        BackupRestoreVerification savedVerification = verificationRepository.save(verification);

        BackupRestoreVerificationItem verified =
                BackupRestoreVerificationItem.verified(savedVerification.getId(), "orders", 12000L);

        BackupRestoreVerificationItem missing = BackupRestoreVerificationItem
                .missing(savedVerification.getId(), "order_items", "expected table is missing");

        verificationItemRepository.saveAll(List.of(verified, missing));

        List<BackupRestoreVerificationItem> missingItems =
                verificationItemRepository.findByVerificationIdAndStatusOrderByTableNameAsc(
                        savedVerification.getId(), BackupRestoreVerificationItemStatus.MISSING);

        assertThat(missingItems).hasSize(1);

        assertThat(missingItems.get(0).getTableName()).isEqualTo("order_items");
    }
}
