package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ParameterValueType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@Component
public class ConfigurationValueComparator {

    public boolean matches(String expectedValue, String actualValue, ParameterValueType valueType) {
        if (expectedValue == null || actualValue == null) {
            return false;
        }

        if (valueType == null) {
            return normalizeString(expectedValue).equals(normalizeString(actualValue));
        }

        return switch (valueType) {
            case BOOLEAN -> compareBoolean(expectedValue, actualValue);
            case NUMBER -> compareNumber(expectedValue, actualValue);
            case STRING -> compareString(expectedValue, actualValue);
        };
    }

    private boolean compareString(String expectedValue, String actualValue) {
        return normalizeString(expectedValue).equals(normalizeString(actualValue));
    }

    private boolean compareNumber(String expectedValue, String actualValue) {
        try {
            BigDecimal expectedNumber = new BigDecimal(expectedValue.trim());

            BigDecimal actualNumber = new BigDecimal(actualValue.trim());

            return expectedNumber.compareTo(actualNumber) == 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean compareBoolean(String expectedValue, String actualValue) {
        Optional<Boolean> expectedBoolean = normalizeBoolean(expectedValue);

        Optional<Boolean> actualBoolean = normalizeBoolean(actualValue);

        if (expectedBoolean.isEmpty() || actualBoolean.isEmpty()) {
            return false;
        }

        return expectedBoolean.get().equals(actualBoolean.get());
    }

    private Optional<Boolean> normalizeBoolean(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "ON", "TRUE", "1", "YES", "Y" -> Optional.of(true);
            case "OFF", "FALSE", "0", "NO", "N" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    private String normalizeString(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
