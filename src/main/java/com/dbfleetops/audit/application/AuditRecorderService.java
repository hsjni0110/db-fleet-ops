package com.dbfleetops.audit.application;

import com.dbfleetops.audit.domain.AuditLog;
import com.dbfleetops.audit.infra.AuditLogRepository;
import com.dbfleetops.audit.port.AuditRecorderPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditRecorderService implements AuditRecorderPort {

    private final AuditLogRepository auditLogRepository;

    public AuditRecorderService(
            AuditLogRepository auditLogRepository
    ) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String actor,
            String action,
            String resourceType,
            String resourceId,
            String result,
            String message
    ) {
        AuditLog auditLog =
                AuditLog.create(
                        actor,
                        action,
                        resourceType,
                        resourceId,
                        result,
                        message
                );

        auditLogRepository.save(auditLog);
    }
}