package com.dbfleetops.backup.domain;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.isTrue;

public record BusinessRule(String code, String name, String query, long timeoutMillis) {
    public BusinessRule {
        hasText(code, "업무 Rule 코드는 필수입니다.");
        hasText(name, "업무 Rule 이름은 필수입니다.");
        hasText(query, "업무 Rule SQL은 필수입니다.");
        isTrue(timeoutMillis > 0, "업무 Rule 제한 시간은 0보다 커야 합니다.");
    }
}
