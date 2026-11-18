package com.dbfleetops.backup.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "business_rule_result", indexes = {
        @Index(name = "idx_business_rule_result_verification_id", columnList = "verificationId"),
        @Index(name = "idx_business_rule_result_rule_code", columnList = "ruleCode"),
        @Index(name = "idx_business_rule_result_status", columnList = "status")})
public class BusinessRuleResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long verificationId;

    private String ruleCode;

    private String ruleName;

    @Enumerated(EnumType.STRING)
    private BusinessRuleStatus status;

    private Long violationCount;

    private Long durationMillis;

    @Column(length = 2000)
    private String message;

    private LocalDateTime createdAt;

    protected BusinessRuleResult() {}

    private BusinessRuleResult(Long verificationId, String ruleCode, String ruleName,
            BusinessRuleStatus status, Long violationCount, Long durationMillis, String message) {
        if (verificationId == null) {
            throw new IllegalArgumentException("verificationId is required.");
        }
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode is required.");
        }
        if (ruleName == null || ruleName.isBlank()) {
            throw new IllegalArgumentException("ruleName is required.");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required.");
        }
        if (violationCount != null && violationCount < 0) {
            throw new IllegalArgumentException("violationCount must be zero or positive.");
        }
        if (durationMillis == null || durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must be zero or positive.");
        }

        this.verificationId = verificationId;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.status = status;
        this.violationCount = violationCount;
        this.durationMillis = durationMillis;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public static BusinessRuleResult create(Long verificationId, String ruleCode, String ruleName,
            BusinessRuleStatus status, Long violationCount, Long durationMillis, String message) {
        return new BusinessRuleResult(verificationId, ruleCode, ruleName, status, violationCount,
                durationMillis, message);
    }
}
