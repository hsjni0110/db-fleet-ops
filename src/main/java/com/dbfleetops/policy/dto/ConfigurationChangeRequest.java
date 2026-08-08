package com.dbfleetops.policy.dto;

import java.util.List;

/** Policy가 검사하고 적용할 설정 변경 내용입니다. */
public record ConfigurationChangeRequest(Long profileId, String requestedBy, String reason,
        List<ConfigurationChangeItem> items) {
}
