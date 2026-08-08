package com.dbfleetops.failure.validity;

import com.dbfleetops.failure.environment.TestEnvironment;
import java.util.Map;

/** 중앙 관제 Process를 재시작해도 작업 장부가 보존되는지 확인합니다. */
final class StatePersistenceCheck implements ArchitectureCheck {
    private final TestEnvironment environment;
    private final String backupParameters;

    StatePersistenceCheck(TestEnvironment environment,
            String backupParameters) {
        this.environment = environment;
        this.backupParameters = backupParameters;
    }

    @Override
    public String title() {
        return "중앙 관제 서버 재시작 뒤 작업 장부 보존";
    }

    @Override
    public String claim() {
        return "작업 장부는 중앙 관제 Process와 분리되어 재시작 뒤에도 남습니다.";
    }

    @Override
    public String criterion() {
        return "중앙 관제 서버 재시작 전후 상태와 Task ID 동일";
    }

    @Override
    public Map<String, Object> measure() throws Exception {
        var agentIdentity = environment.registerAgent("validity-state");
        long taskId = environment.createTask(agentIdentity.id(), null, backupParameters);
        String statusBeforeRestart = environment.taskStatus(taskId);

        environment.restartControlPlane();

        String statusAfterRestart = environment.taskStatus(taskId);
        long returnedTaskId = environment.claimNextTask(agentIdentity)
                .body().path("taskId").asLong();
        return CheckValues.inOrder(
                "재시작 전 Task 상태", statusBeforeRestart,
                "재시작 후 Task 상태", statusAfterRestart,
                "원래 Task ID", taskId,
                "재시작 후 조회한 Task ID", returnedTaskId,
                "중앙 API 상태", environment.get("/actuator/health").status());
    }

    @Override
    public boolean supports(Map<String, Object> measurements) {
        return measurements.get("재시작 전 Task 상태").equals(
                        measurements.get("재시작 후 Task 상태"))
                && measurements.get("원래 Task ID").equals(
                        measurements.get("재시작 후 조회한 Task ID"))
                && ((Number) measurements.get("중앙 API 상태")).intValue() == 200;
    }

    @Override
    public String limitation() {
        return "Database 자체의 장애와 복제·복구 능력은 이 실험 범위가 아닙니다.";
    }

}
