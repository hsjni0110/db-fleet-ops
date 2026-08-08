package com.dbfleetops.failure.scenario;

import com.dbfleetops.failure.environment.TestEnvironment;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.ToxiproxyContainer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("agent-failure")
class AgentFailureTest {
    private static final String BACKUP_PARAMETERS = """
            {"databaseName":"evidence","host":"managed-mysql","port":3306,
             "username":"evidence_user","password":"evidence_password",
             "backupType":"LOGICAL","compression":false,"verifyAfterBackup":false}
            """;
    private static final String RESULT_REPORT_ID = "8d77288c-cf64-4ae8-a5be-a4010192fc6e";

    private static TestEnvironment environment;

    @BeforeAll
    static void startEnvironment() throws Exception {
        boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        boolean dockerRequired = Boolean.parseBoolean(
                System.getProperty("failureTest.requireDocker", "false"));
        if (!dockerAvailable && dockerRequired) {
            throw new IllegalStateException(
                    "FAILURE_TEST_REQUIRE_DOCKER=true이지만 Docker를 사용할 수 없습니다.");
        }
        assumeTrue(dockerAvailable, "Docker를 사용할 수 없어 실패 재현 테스트를 건너뜁니다.");

        System.out.println("[환경] 실패 재현용 MySQL과 Control Plane을 시작합니다.");
        environment = new TestEnvironment();
        environment.start();
    }

    @AfterAll
    static void stopEnvironment() {
        if (environment != null) environment.close();
    }

    @Test
    @Order(1)
    void heartbeatContinuesDuringLongBackup() throws Exception {
        var agent = environment.registerAgent("heartbeat");
        GenericContainer<?> agentContainer = environment.startAgent(agent, 20);
        try {
            List<Instant> heartbeats = observeInitialHeartbeats(agent);
            long taskId = environment.createTask(agent.id(), null, BACKUP_PARAMETERS);

            assertTrue(environment.await(Duration.ofSeconds(10),
                    env -> "RUNNING".equals(env.taskStatus(taskId))));
            collectHeartbeatsUntilTaskEnds(agent, taskId, heartbeats);

            assertEquals("SUCCEEDED", environment.taskStatus(taskId));
            assertTrue(maxGapSeconds(heartbeats) <= 5,
                    "긴 Backup 중 Heartbeat 간격이 5초를 넘었습니다.");
            printResult("긴 Backup 중 Heartbeat", Map.of(
                    "최대 Heartbeat 간격(초)", maxGapSeconds(heartbeats),
                    "Heartbeat 표본 수", heartbeats.size(),
                    "Task 상태", environment.taskStatus(taskId)));
        } finally {
            agentContainer.stop();
        }
    }

    @Test
    @Order(2)
    void expiredTaskIsRecoveredAfterAgentKill() throws Exception {
        var agent = environment.registerAgent("kill");
        long taskId = environment.createTask(agent.id(), null, BACKUP_PARAMETERS);
        GenericContainer<?> agentContainer = environment.startAgent(agent, 30);
        try {
            assertTrue(environment.await(Duration.ofSeconds(10),
                    env -> "RUNNING".equals(env.taskStatus(taskId))));
            Instant heartbeatBeforeKill = environment.heartbeatAt(agent.id());

            agentContainer.getDockerClient().killContainerCmd(agentContainer.getContainerId())
                    .withSignal("SIGKILL").exec();

            assertTrue(environment.await(Duration.ofSeconds(20), env -> {
                String status = env.taskStatus(taskId);
                return "QUEUED".equals(status) || "TIMED_OUT".equals(status);
            }), "종료된 Agent의 Task Lease가 회수되지 않았습니다.");

            assertEquals(heartbeatBeforeKill, environment.heartbeatAt(agent.id()));
            printResult("Agent 종료 후 Task Lease 회수", Map.of(
                    "Task 상태", environment.taskStatus(taskId),
                    "Heartbeat 중단", true));
        } finally {
            agentContainer.stop();
        }
    }

    @Test
    @Order(3)
    void concurrentPollClaimsTaskOnce() throws Exception {
        var agent = environment.registerAgent("concurrent-poll");
        long taskId = environment.createTask(agent.id(), null, BACKUP_PARAMETERS);
        int duplicateClaims = 0;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < 20; round++) {
                CountDownLatch barrier = new CountDownLatch(1);
                var first = executor.submit(() -> pollAtBarrier(agent, barrier));
                var second = executor.submit(() -> pollAtBarrier(agent, barrier));
                barrier.countDown();
                if (first.get() == taskId && second.get() == taskId) duplicateClaims++;
            }
        }

        assertEquals(0, duplicateClaims);
        printResult("동시 Poll의 중복 선점", Map.of(
                "반복 횟수", 20,
                "같은 Task 중복 반환 횟수", duplicateClaims));
    }

    @Test
    @Order(4)
    void duplicateResultReportIsIdempotent() throws Exception {
        var agent = environment.registerAgent("lost-response");
        long jobId = environment.insertRunningJob();
        String parameters = BACKUP_PARAMETERS.replace("\"verifyAfterBackup\":false",
                "\"verifyAfterBackup\":true");
        long taskId = environment.createTask(agent.id(), jobId, parameters);
        int executionAttempt = environment.claimNextTask(agent).body()
                .path("executionAttempt").asInt();
        var proxy = environment.startResponseLossProxy();

        boolean firstResponseLost = loseFirstCompletionResponse(
                agent, taskId, executionAttempt, proxy);
        assertTrue(environment.await(Duration.ofSeconds(10),
                env -> "SUCCEEDED".equals(env.taskStatus(taskId))));

        int retryStatus = completeTask(environment.baseUrl(), agent, taskId,
                executionAttempt, Duration.ofSeconds(5)).status();
        long restoreTaskCount = environment.countTasks(jobId, "MYSQL_RESTORE_VERIFY");

        assertTrue(firstResponseLost);
        assertEquals(200, retryStatus);
        assertEquals(1, restoreTaskCount);
        printResult("완료 응답 유실 후 같은 결과 재전송", Map.of(
                "재전송 HTTP 상태", retryStatus,
                "복원 확인 Task 수", restoreTaskCount));
    }

    private static List<Instant> observeInitialHeartbeats(
            TestEnvironment.AgentIdentity agent) throws Exception {
        Instant first = environment.heartbeatAt(agent.id());
        assertTrue(environment.await(Duration.ofSeconds(6),
                env -> !Objects.equals(first, env.heartbeatAt(agent.id()))));
        List<Instant> heartbeats = new ArrayList<>();
        heartbeats.add(environment.heartbeatAt(agent.id()));
        return heartbeats;
    }

    private static void collectHeartbeatsUntilTaskEnds(
            TestEnvironment.AgentIdentity agent, long taskId,
            List<Instant> heartbeats) throws Exception {
        Instant deadline = Instant.now().plusSeconds(28);
        Instant previous = heartbeats.getLast();
        while (Instant.now().isBefore(deadline)) {
            Instant current = environment.heartbeatAt(agent.id());
            if (current != null && !current.equals(previous)) {
                heartbeats.add(current);
                previous = current;
            }
            if ("SUCCEEDED".equals(environment.taskStatus(taskId))) return;
            Thread.sleep(500);
        }
    }

    private static long pollAtBarrier(TestEnvironment.AgentIdentity agent,
            CountDownLatch barrier) throws Exception {
        barrier.await();
        return environment.claimNextTask(agent).body().path("taskId").asLong();
    }

    private static boolean loseFirstCompletionResponse(
            TestEnvironment.AgentIdentity agent, long taskId,
            int executionAttempt, ToxiproxyContainer.ContainerProxy proxy) throws Exception {
        proxy.toxics().timeout("drop-complete-response", ToxicDirection.DOWNSTREAM, 0);
        try {
            completeTask(environment.proxyBaseUrl(proxy), agent, taskId,
                    executionAttempt, Duration.ofSeconds(2));
            return false;
        } catch (IOException expected) {
            return true;
        } finally {
            proxy.toxics().get("drop-complete-response").remove();
        }
    }

    private static TestEnvironment.HttpResult completeTask(String base,
            TestEnvironment.AgentIdentity agent, long taskId,
            int executionAttempt, Duration timeout) throws Exception {
        String result = "{\"status\":\"VERIFIED\",\"backupFile\":\"/tmp/evidence.sql\","
                + "\"fileSizeBytes\":100,\"checksumSha256\":\"abc123\","
                + "\"createdAt\":\"2026-08-05T00:00:00Z\",\"message\":\"verified\"}";
        return environment.postTo(base,
                "/internal/v1/agents/" + agent.id() + "/tasks/" + taskId + "/complete",
                Map.of("agentToken", agent.token(), "executionAttempt", executionAttempt,
                        "resultReportId", RESULT_REPORT_ID,
                        "resultPayloadJson", result), timeout);
    }

    private static long maxGapSeconds(List<Instant> heartbeats) {
        long max = 0;
        for (int index = 1; index < heartbeats.size(); index++) {
            max = Math.max(max, Duration.between(heartbeats.get(index - 1), heartbeats.get(index))
                    .toSeconds());
        }
        return max;
    }

    private static void printResult(String scenario, Map<String, Object> values) {
        System.out.println();
        System.out.println("[실패 재현] " + scenario);
        values.forEach((name, value) -> System.out.println("  - " + name + ": " + value));
    }
}
