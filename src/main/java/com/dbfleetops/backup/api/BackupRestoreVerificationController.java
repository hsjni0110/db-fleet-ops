package com.dbfleetops.backup.api;

import com.dbfleetops.backup.application.BackupRestoreVerificationQueryService;
import com.dbfleetops.backup.dto.BackupRestoreVerificationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BackupRestoreVerificationController {

    private final BackupRestoreVerificationQueryService queryService;

    public BackupRestoreVerificationController(BackupRestoreVerificationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/api/v1/jobs/{jobId}/restore-verification")
    public BackupRestoreVerificationResponse getByOperationJobId(@PathVariable Long jobId) {
        return queryService.getByOperationJobId(jobId);
    }

    @GetMapping("/api/v1/databases/{databaseId}/restore-verifications/latest")
    public BackupRestoreVerificationResponse getLatestByDatabaseId(@PathVariable Long databaseId) {
        return queryService.getLatestByDatabaseId(databaseId);
    }

    @GetMapping("/api/v1/restore-verifications/{verificationId}")
    public BackupRestoreVerificationResponse getById(@PathVariable Long verificationId) {
        return queryService.getById(verificationId);
    }
}
