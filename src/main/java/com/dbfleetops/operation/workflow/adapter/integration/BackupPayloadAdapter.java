package com.dbfleetops.operation.workflow.adapter.integration;

import com.dbfleetops.backup.application.RestoreVerifyTaskPayloadFactory;
import com.dbfleetops.operation.workflow.application.required.BackupPayloadBuilder;
import org.springframework.stereotype.Component;

@Component
public class BackupPayloadAdapter implements BackupPayloadBuilder {
    private final RestoreVerifyTaskPayloadFactory factory;
    public BackupPayloadAdapter(RestoreVerifyTaskPayloadFactory factory) { this.factory = factory; }
    public boolean shouldVerifyAfterBackup(String parameters) {
        return factory.parseBackupTaskPayload(parameters).shouldVerifyAfterBackup();
    }
    public String createRestorePayload(Long jobId, Long taskId, String parameters, String result) {
        return factory.createRestoreVerifyTaskPayloadJson(jobId, taskId, parameters, result);
    }
}
