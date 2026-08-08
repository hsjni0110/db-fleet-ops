package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.operation.application.required.AuditWriter;
import org.springframework.stereotype.Component;

@Component
public class AuditContextAdapter implements AuditWriter {
    private final AuditRecorderPort recorder;
    public AuditContextAdapter(AuditRecorderPort recorder) { this.recorder = recorder; }
    public void record(String actor, String action, String type, String id, String result,
            String message) { recorder.record(actor, action, type, id, result, message); }
}
