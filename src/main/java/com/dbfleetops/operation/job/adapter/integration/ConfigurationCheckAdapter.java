package com.dbfleetops.operation.job.adapter.integration;

import com.dbfleetops.operation.job.application.required.ConfigurationCheck;
import com.dbfleetops.operation.job.application.required.ConfigurationCheckCommand;
import com.dbfleetops.operation.job.application.required.ConfigurationCheckOutcome;
import com.dbfleetops.operation.shared.application.required.DatabaseExecutionTarget;
import com.dbfleetops.operation.shared.application.required.DatabaseReader;
import com.dbfleetops.policy.application.ConfigurationCheckExecution;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import org.springframework.stereotype.Component;

/** Operation의 설정 점검 요청을 설정 영역의 수집·비교 기능에 연결합니다. */
@Component
public class ConfigurationCheckAdapter implements ConfigurationCheck {

    private final DatabaseReader databases;
    private final ConfigurationCheckExecution checks;

    public ConfigurationCheckAdapter(DatabaseReader databases, ConfigurationCheckExecution checks) {
        this.databases = databases;
        this.checks = checks;
    }

    @Override
    public ConfigurationCheckOutcome check(ConfigurationCheckCommand command) {
        DatabaseExecutionTarget database = requireDatabase(command.databaseId());
        var result = checks.check(database.id(), engineOf(database), command.profileId());
        return new ConfigurationCheckOutcome(result.driftId(), result.status().name());
    }

    private DatabaseExecutionTarget requireDatabase(Long databaseId) {
        return databases.findDatabase(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "관리 DB를 찾을 수 없습니다. databaseId=" + databaseId));
    }

    private ConfigurationEngineType engineOf(DatabaseExecutionTarget database) {
        if (database.engine() == null) {
            throw new IllegalArgumentException(
                    "DBMS 종류가 필요합니다. databaseId=" + database.id());
        }
        return ConfigurationEngineType.valueOf(database.engine());
    }
}
