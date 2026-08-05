package com.dbfleetops.audit.domain;

import lombok.Getter;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String actor;

    private String action;

    private String resourceType;

    private String resourceId;

    private String result;

    @Column(length = 2000)
    private String message;

    private LocalDateTime createdAt;

    protected AuditLog() {
    }

    private AuditLog(
            String actor,
            String action,
            String resourceType,
            String resourceId,
            String result,
            String message
    ) {
        this.actor = actor;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.result = result;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public static AuditLog create(
            String actor,
            String action,
            String resourceType,
            String resourceId,
            String result,
            String message
    ) {
        return new AuditLog(
                actor,
                action,
                resourceType,
                resourceId,
                result,
                message
        );
    }








}