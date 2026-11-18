package com.dbfleetops.backup.domain;

import com.dbfleetops.database.domain.DatabaseEngine;

import java.util.List;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notEmpty;
import static org.springframework.util.Assert.notNull;

public record BusinessVerificationProfile(String key, String name, String description,
        DatabaseEngine engine, List<String> requiredTables, List<BusinessRule> rules) {

    public BusinessVerificationProfile {
        hasText(key, "업무 검증 Profile Key는 필수입니다.");
        hasText(name, "업무 검증 Profile 이름은 필수입니다.");
        hasText(description, "업무 검증 Profile 설명은 필수입니다.");
        notNull(engine, "업무 검증 Profile DBMS는 필수입니다.");
        notEmpty(requiredTables, "업무 검증 Profile 필수 table은 비어 있을 수 없습니다.");
        notEmpty(rules, "업무 검증 Profile Rule은 비어 있을 수 없습니다.");
        requiredTables = List.copyOf(requiredTables);
        rules = List.copyOf(rules);
    }
}
