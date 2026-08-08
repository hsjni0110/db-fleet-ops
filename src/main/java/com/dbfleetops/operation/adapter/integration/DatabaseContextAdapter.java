package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.database.domain.DatabaseStatus;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.application.required.DatabaseReader;
import com.dbfleetops.operation.application.required.DatabaseExecutionTarget;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 관리 대상 Database 저장소를 Operation의 Database 조회 포트에 연결합니다. */
@Component
public class DatabaseContextAdapter implements DatabaseReader {
    private final ManagedDatabaseRepository databases;

    public DatabaseContextAdapter(ManagedDatabaseRepository databases) {
        this.databases = databases;
    }

    @Override
    public Optional<DatabaseExecutionTarget> findDatabase(Long databaseId) {
        return databases.findById(databaseId).map(database -> new DatabaseExecutionTarget(
                database.getId(), database.getDatabaseName(), database.getHost(), database.getPort(),
                database.getEngine() == null ? null : database.getEngine().name(),
                database.getAssignedAgentId(), database.getStatus() == DatabaseStatus.ACTIVE));
    }
}
