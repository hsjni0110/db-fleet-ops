package com.dbfleetops.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuration_snapshot_item")
public class ConfigurationSnapshotItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long snapshotId;

    private String parameterName;

    @Column(columnDefinition = "TEXT")
    private String actualValue;

    private String unit;

    private String valueType;

    private Boolean dynamic;

    private String source;

    private LocalDateTime createdAt;

    protected ConfigurationSnapshotItem() {}

    private ConfigurationSnapshotItem(Long snapshotId, String parameterName, String actualValue,
            String unit, String valueType, Boolean dynamic, String source) {
        if (snapshotId == null) {
            throw new IllegalArgumentException("snapshotId is required.");
        }

        validateRequiredText(parameterName, "parameterName");

        this.snapshotId = snapshotId;
        this.parameterName = parameterName;
        this.actualValue = actualValue;
        this.unit = unit;
        this.valueType = valueType;
        this.dynamic = dynamic;
        this.source = source;
        this.createdAt = LocalDateTime.now();
    }

    public static ConfigurationSnapshotItem create(Long snapshotId, String parameterName,
            String actualValue, String unit, String valueType, Boolean dynamic, String source) {
        return new ConfigurationSnapshotItem(snapshotId, parameterName, actualValue, unit,
                valueType, dynamic, source);
    }

    private static void validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getActualValue() {
        return actualValue;
    }

    public String getUnit() {
        return unit;
    }

    public String getValueType() {
        return valueType;
    }

    public Boolean getDynamic() {
        return dynamic;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
