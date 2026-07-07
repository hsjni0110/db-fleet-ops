package com.dbfleetops.database.dto;

public record DatabaseConfigurationItem(String parameterName, String actualValue, String unit,
        String valueType, Boolean dynamic, String source) {
}
