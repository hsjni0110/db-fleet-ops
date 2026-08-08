package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.backup.application.BackupRestoreVerificationResultRecorder;
import com.dbfleetops.operation.application.required.BackupVerificationWriter;
import com.dbfleetops.operation.application.required.RestoreVerificationOutcome;
import org.springframework.stereotype.Component;

@Component
public class BackupContextAdapter implements BackupVerificationWriter {
    private final BackupRestoreVerificationResultRecorder recorder;
    public BackupContextAdapter(BackupRestoreVerificationResultRecorder recorder) {
        this.recorder = recorder;
    }
    public RestoreVerificationOutcome record(Long jobId, Long taskId, String payload) {
        var result = recorder.record(jobId, taskId, payload);
        return new RestoreVerificationOutcome(result.isVerified(), result.errorCode(),
                result.errorMessage() == null ? result.message() : result.errorMessage());
    }
}
