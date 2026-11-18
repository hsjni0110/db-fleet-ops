package com.dbfleetops.backup.application;

import com.dbfleetops.backup.domain.BackupRestoreVerification;
import com.dbfleetops.backup.dto.BackupRestoreVerificationItemResponse;
import com.dbfleetops.backup.dto.BackupRestoreVerificationResponse;
import com.dbfleetops.backup.dto.BusinessRuleResultResponse;
import com.dbfleetops.backup.infra.BackupRestoreVerificationItemRepository;
import com.dbfleetops.backup.infra.BackupRestoreVerificationRepository;
import com.dbfleetops.backup.infra.BusinessRuleResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BackupRestoreVerificationQueryService {

    private final BackupRestoreVerificationRepository verificationRepository;
    private final BackupRestoreVerificationItemRepository itemRepository;
    private final BusinessRuleResultRepository ruleResultRepository;

    @Autowired
    public BackupRestoreVerificationQueryService(
            BackupRestoreVerificationRepository verificationRepository,
            BackupRestoreVerificationItemRepository itemRepository,
            BusinessRuleResultRepository ruleResultRepository) {
        this.verificationRepository = verificationRepository;
        this.itemRepository = itemRepository;
        this.ruleResultRepository = ruleResultRepository;
    }

    public BackupRestoreVerificationQueryService(
            BackupRestoreVerificationRepository verificationRepository,
            BackupRestoreVerificationItemRepository itemRepository) {
        this(verificationRepository, itemRepository, null);
    }

    @Transactional(readOnly = true)
    public BackupRestoreVerificationResponse getById(Long verificationId) {
        BackupRestoreVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Backup restore verification not found. verificationId=" + verificationId));

        return toResponse(verification);
    }

    @Transactional(readOnly = true)
    public BackupRestoreVerificationResponse getByOperationJobId(Long operationJobId) {
        BackupRestoreVerification verification = verificationRepository
                .findByOperationJobId(operationJobId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Backup restore verification not found. operationJobId=" + operationJobId));

        return toResponse(verification);
    }

    @Transactional(readOnly = true)
    public BackupRestoreVerificationResponse getLatestByDatabaseId(Long databaseId) {
        BackupRestoreVerification verification =
                verificationRepository.findFirstByDatabaseIdOrderByCreatedAtDesc(databaseId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Backup restore verification not found. databaseId=" + databaseId));

        return toResponse(verification);
    }

    private BackupRestoreVerificationResponse toResponse(BackupRestoreVerification verification) {
        List<BackupRestoreVerificationItemResponse> items =
                itemRepository.findByVerificationIdOrderByTableNameAsc(verification.getId())
                        .stream().map(BackupRestoreVerificationItemResponse::from).toList();

        List<BusinessRuleResultResponse> ruleResults = ruleResultRepository == null ? List.of()
                : ruleResultRepository
                        .findByVerificationIdOrderByRuleCodeAsc(verification.getId()).stream()
                        .map(BusinessRuleResultResponse::from).toList();

        return BackupRestoreVerificationResponse.from(verification, items, ruleResults);
    }
}
