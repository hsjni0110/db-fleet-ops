package com.dbfleetops.health.infra;

import com.dbfleetops.database.application.CredentialCipher;
import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.dto.RegisterManagedDatabaseRequest;
import com.dbfleetops.health.domain.LockWaitInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class MySqlLockWaitIntegrationTest {
    private static final int WORKERS = 4;
    private static final int REQUESTS_PER_WORKER = 12;
    private static final int WORK_DELAY_MS = 180;
    private static final long TIMEOUT = -1;

    @Test
    void diagnosesBlockedWritesAndMeasuresShorterTransactions() throws Exception {
        assumeTrue(System.getenv("LOCK_LAB_DATABASE") != null, "Set LOCK_LAB_DATABASE to run the lock experiment.");
        int port = Integer.parseInt(requiredEnv("LOCK_LAB_PORT"));
        String schema = requiredEnv("LOCK_LAB_DATABASE");
        if (!schema.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Use a simple schema name for the lock lab.");
        }
        String username = requiredEnv("LOCK_LAB_USER");
        String password = requiredEnv("LOCK_LAB_PASSWORD");
        String url = "jdbc:mysql://127.0.0.1:" + port + "/" + schema
                + "?connectTimeout=3000&socketTimeout=3000";
        var cipher = new CredentialCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        var credential = new DatabaseCredential(1L, username, cipher.encrypt(password));
        var database = ManagedDatabase.register(new RegisterManagedDatabaseRequest(
                "Lock lab", "127.0.0.1", port, schema, DatabaseEngine.MYSQL, "test", null, null, null));
        var diagnostic = new MySqlDiagnosticAdapter(cipher);
        String table = "lock_lab_" + UUID.randomUUID().toString().replace("-", "");
        String write = "UPDATE " + table + " SET hits = hits + 1 WHERE id = 1";

        try (var control = DriverManager.getConnection(url, username, password);
             var pool = Executors.newFixedThreadPool(WORKERS)) {
            execute(control, "CREATE TABLE " + table + " (id INT PRIMARY KEY, hits INT NOT NULL) ENGINE=InnoDB");
            try {
                System.out.println("Lock lab MySQL " + control.getMetaData().getDatabaseProductVersion());
                execute(control, "INSERT INTO " + table + " VALUES (1, 0)");
                try (var blocker = DriverManager.getConnection(url, username, password);
                     var waiter = DriverManager.getConnection(url, username, password)) {
                    long blockerId = value(blocker, "SELECT CONNECTION_ID()");
                    long waiterId = value(waiter, "SELECT CONNECTION_ID()");
                    execute(waiter, "SET SESSION innodb_lock_wait_timeout = 10");
                    blocker.setAutoCommit(false);
                    execute(blocker, write);
                    Future<?> pending = pool.submit(() -> { execute(waiter, write); return null; });
                    try {
                        LockWaitInfo wait = awaitWait(diagnostic, database, credential, waiterId, blockerId);
                        assertThat(wait.waitingTransactionId()).isNotBlank();
                        assertThat(wait.blockingTransactionId()).isNotBlank().isNotEqualTo(wait.waitingTransactionId());
                        System.out.printf("Observed lock: waiter=%d (trx %s), blocker=%d (trx %s)%n",
                                waiterId, wait.waitingTransactionId(), blockerId, wait.blockingTransactionId());
                    } finally {
                        blocker.rollback();
                        pending.get(12, TimeUnit.SECONDS);
                    }
                    assertThat(diagnostic.getLockWaits(database, credential)).noneMatch(wait ->
                            wait.waitingThreadId() == waiterId && wait.blockingThreadId() == blockerId);
                }

                for (Phase phase : Phase.values()) {
                    execute(control, "UPDATE " + table + " SET hits = 0 WHERE id = 1");
                    var starts = new CyclicBarrier(WORKERS);
                    var workerIds = ConcurrentHashMap.<Long>newKeySet();
                    List<Future<List<Long>>> runs = new ArrayList<>();
                    for (int worker = 0; worker < WORKERS; worker++) {
                        runs.add(pool.submit(() -> measure(url, username, password, write, phase, starts, workerIds)));
                    }
                    int observedSamples = 0;
                    int samplesWithWaits = 0;
                    long observationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
                    while (runs.stream().anyMatch(run -> !run.isDone()) && System.nanoTime() < observationDeadline) {
                        boolean hasWaits = diagnostic.getLockWaits(database, credential).stream().anyMatch(wait ->
                                workerIds.contains(wait.waitingThreadId()) && workerIds.contains(wait.blockingThreadId()));
                        observedSamples++;
                        if (hasWaits) {
                            samplesWithWaits++;
                        }
                        Thread.sleep(50);
                    }
                    List<Long> samples = new ArrayList<>();
                    for (var run : runs) {
                        samples.addAll(run.get(25, TimeUnit.SECONDS));
                    }
                    var completed = samples.stream().filter(sample -> sample != TIMEOUT).sorted().toList();
                    long finalHits = value(control, "SELECT hits FROM " + table + " WHERE id = 1");
                    assertThat(samples).hasSize(WORKERS * REQUESTS_PER_WORKER);
                    assertThat(completed).isNotEmpty();
                    assertThat(finalHits).isEqualTo(completed.size());
                    if (phase == Phase.DELAY_WITH_LOCK) {
                        assertThat(samplesWithWaits).isPositive();
                    }
                    long p95 = completed.get((int) Math.ceil(completed.size() * 0.95) - 1);
                    System.out.printf(Locale.ROOT, "%s attempts=%d success=%d timeouts=%d p95_ms=%.2f final_hits=%d wait_samples=%d/%d%n",
                            phase, samples.size(), completed.size(), samples.size() - completed.size(),
                            p95 / 1_000_000.0, finalHits, samplesWithWaits, observedSamples);
                }
            } finally {
                execute(control, "DROP TABLE " + table);
            }
        }
    }

    private static LockWaitInfo awaitWait(MySqlDiagnosticAdapter diagnostic, ManagedDatabase database,
            DatabaseCredential credential, long waiterId, long blockerId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            for (var wait : diagnostic.getLockWaits(database, credential)) {
                if (wait.waitingThreadId() == waiterId && wait.blockingThreadId() == blockerId) {
                    return wait;
                }
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Did not observe waiter " + waiterId + " blocked by " + blockerId);
    }

    private static List<Long> measure(String url, String username, String password, String write,
            Phase phase, CyclicBarrier starts, Set<Long> workerIds) throws Exception {
        try (var connection = DriverManager.getConnection(url, username, password)) {
            execute(connection, "SET SESSION innodb_lock_wait_timeout = 1");
            workerIds.add(value(connection, "SELECT CONNECTION_ID()"));
            connection.setAutoCommit(false);
            starts.await(5, TimeUnit.SECONDS);
            List<Long> samples = new ArrayList<>();
            for (int request = 0; request < REQUESTS_PER_WORKER; request++) {
                long start = System.nanoTime();
                try {
                    if (phase == Phase.DELAY_BEFORE_LOCK) {
                        Thread.sleep(WORK_DELAY_MS);
                    }
                    execute(connection, write);
                    if (phase == Phase.DELAY_WITH_LOCK) {
                        Thread.sleep(WORK_DELAY_MS);
                    }
                    connection.commit();
                    samples.add(System.nanoTime() - start);
                } catch (SQLException exception) {
                    connection.rollback();
                    if (exception.getErrorCode() != 1205) {
                        throw exception;
                    }
                    samples.add(TIMEOUT);
                }
            }
            return samples;
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static long value(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new SQLException("Expected one row from lock lab query");
            }
            return result.getLong(1);
        }
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + key + " for the disposable MySQL lock lab.");
        }
        return value;
    }

    private enum Phase { BASELINE, DELAY_WITH_LOCK, DELAY_BEFORE_LOCK }
}
