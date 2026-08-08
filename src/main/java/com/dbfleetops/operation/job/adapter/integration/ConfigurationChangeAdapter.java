package com.dbfleetops.operation.job.adapter.integration;

import com.dbfleetops.operation.job.application.required.ConfigurationApplyOutcome;
import com.dbfleetops.operation.job.application.required.ConfigurationChange;
import com.dbfleetops.operation.job.application.required.ConfigurationChangeCommand;
import com.dbfleetops.operation.shared.application.required.DatabaseExecutionTarget;
import com.dbfleetops.operation.shared.application.required.DatabaseReader;
import com.dbfleetops.policy.application.ConfigurationChangeExecution;
import com.dbfleetops.policy.domain.ConfigurationApplyStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.dto.ConfigurationChangeItem;
import com.dbfleetops.policy.dto.ConfigurationChangeRequest;
import org.springframework.stereotype.Component;

/** Operation의 설정 변경 요청을 설정 영역의 검증·적용 기능에 연결합니다. */
@Component
public class ConfigurationChangeAdapter implements ConfigurationChange {

    private final DatabaseReader databases;
    private final ConfigurationChangeExecution changes;

    public ConfigurationChangeAdapter(DatabaseReader databases, ConfigurationChangeExecution changes) {
        this.databases = databases;
        this.changes = changes;
    }

    @Override
    public void validate(ConfigurationChangeCommand command) {
        requireDatabase(command.databaseId());
        changes.validate(command.databaseId(), toRequest(command));
    }

    @Override
    public ConfigurationApplyOutcome apply(ConfigurationChangeCommand command) {
        DatabaseExecutionTarget database = requireDatabase(command.databaseId());
        var result = changes.apply(command.jobId(), database.id(), engineOf(database),
                toRequest(command));

        return new ConfigurationApplyOutcome(result.getId(),
                result.getStatus() == ConfigurationApplyStatus.SUCCEEDED,
                result.getStatus().name(), result.getSuccessCount(), result.getFailedCount(),
                result.getSkippedCount());
    }

    private ConfigurationChangeRequest toRequest(ConfigurationChangeCommand command) {
        return new ConfigurationChangeRequest(command.profileId(), command.requestedBy(),
                command.reason(), command.items().stream()
                        .map(item -> new ConfigurationChangeItem(
                                item.parameterName(), item.targetValue()))
                        .toList());
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
