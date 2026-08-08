package com.dbfleetops.operation.job.application.required;

/** 변경할 설정 항목 하나입니다. */
public record ConfigurationChangeItem(String parameterName, String targetValue) {
}
