package com.dbfleetops.health.port;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.health.domain.ConnectionSummary;
import com.dbfleetops.health.domain.DatabaseUptimeInfo;
import com.dbfleetops.health.domain.DatabaseVersionInfo;
import com.dbfleetops.health.domain.LockWaitInfo;
import com.dbfleetops.health.domain.LongTransactionInfo;
import com.dbfleetops.health.domain.SessionInfo;
import com.dbfleetops.health.domain.SlowQueryInfo;

import java.util.List;

public interface DatabaseDiagnosticPort {

    DatabaseEngine supports();

    DatabaseVersionInfo getVersion(
            ManagedDatabase database,
            DatabaseCredential credential
    );

    DatabaseUptimeInfo getUptime(
            ManagedDatabase database,
            DatabaseCredential credential
    );

    ConnectionSummary getConnectionSummary(
            ManagedDatabase database,
            DatabaseCredential credential
    );

    List<SessionInfo> getSessions(
            ManagedDatabase database,
            DatabaseCredential credential
    );

    List<LongTransactionInfo> getLongTransactions(
            ManagedDatabase database,
            DatabaseCredential credential
    );

    List<LockWaitInfo> getLockWaits(
            ManagedDatabase database,
            DatabaseCredential credential
    );

    List<SlowQueryInfo> getSlowQueries(
            ManagedDatabase database,
            DatabaseCredential credential
    );
}