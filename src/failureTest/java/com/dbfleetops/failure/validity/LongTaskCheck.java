package com.dbfleetops.failure.validity;

import com.dbfleetops.failure.environment.TestEnvironment;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 긴 작업의 실행 장소와 중앙 관제 서버의 응답이 실제로 분리되는지 확인합니다. */
final class LongTaskCheck
        implements ArchitectureCheck {
    private static final int BACKUP_SECONDS = 20;
    private static final long MAXIMUM_RESPONSE_MILLIS = 2_000;

    private final TestEnvironment environment;
    private final String backupParameters;

    LongTaskCheck(TestEnvironment environment,
            String backupParameters) {
        this.environment = environment;
        this.backupParameters = backupParameters;
    }

    @Override
    public String title() {
        return "긴 현장 작업과 중앙 관제 응답의 분리";
    }

    @Override
    public String claim() {
        return "긴 작업은 현장 실행기에서 수행되고 중앙 관제 서버는 계속 응답합니다.";
    }

    @Override
    public String criterion() {
        return "20초 Backup 중 상태 API 성공률 100%, 모든 응답 2초 미만";
    }

    @Override
    public Map<String, Object> measure() throws Exception {
        var agentIdentity = environment.registerAgent("validity-load");
        long taskId = environment.createTask(agentIdentity.id(), null, backupParameters);
        GenericContainer<?> agent = environment.startAgent(agentIdentity, BACKUP_SECONDS);

        try {
            waitUntilTaskStarts(taskId);
            return observeControlPlaneUntilTaskFinishes(taskId);
        } finally {
            agent.stop();
        }
    }

    private void waitUntilTaskStarts(long taskId) throws Exception {
        if (!environment.await(Duration.ofSeconds(10),
                current -> "RUNNING".equals(current.taskStatus(taskId)))) {
            throw new IllegalStateException("Backup Task가 실행을 시작하지 않았습니다.");
        }
    }

    private Map<String, Object> observeControlPlaneUntilTaskFinishes(long taskId)
            throws Exception {
        List<Long> responseTimes = new ArrayList<>();
        int successfulCalls = 0;

        while (!"SUCCEEDED".equals(environment.taskStatus(taskId))) {
            long startedAt = System.nanoTime();
            var response = environment.get("/actuator/health");
            responseTimes.add(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            if (response.status() == 200) successfulCalls++;
            Thread.sleep(500);
        }

        long maximumResponseTime = responseTimes.stream().mapToLong(Long::longValue)
                .max().orElse(-1);
        double successRate = successfulCalls * 100.0 / responseTimes.size();

        return CheckValues.inOrder(
                "중앙 API 호출 수", responseTimes.size(),
                "성공률(%)", successRate,
                "최대 응답 시간(ms)", maximumResponseTime,
                "Backup 최종 상태", environment.taskStatus(taskId));
    }

    @Override
    public boolean supports(Map<String, Object> measurements) {
        double successRate = ((Number) measurements.get("성공률(%)")).doubleValue();
        long maximum = ((Number) measurements.get("최대 응답 시간(ms)")).longValue();
        return successRate == 100.0 && maximum < MAXIMUM_RESPONSE_MILLIS;
    }

    @Override
    public String limitation() {
        return "가짜 Backup은 대기 시간을 재현합니다. 실제 대용량 Backup의 CPU와 Disk 부하는 별도로 측정해야 합니다.";
    }

}
