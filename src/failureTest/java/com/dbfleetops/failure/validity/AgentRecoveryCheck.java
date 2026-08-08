package com.dbfleetops.failure.validity;

import com.dbfleetops.failure.environment.TestEnvironment;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.Map;

/** 한 현장 실행기의 Process 장애가 다른 실행기와 중앙 서버로 번지는지 확인합니다. */
final class AgentRecoveryCheck implements ArchitectureCheck {
    private final TestEnvironment environment;
    private final String backupParameters;

    AgentRecoveryCheck(TestEnvironment environment,
            String backupParameters) {
        this.environment = environment;
        this.backupParameters = backupParameters;
    }

    @Override
    public String title() {
        return "한 현장 실행기의 장애가 다른 실행기로 번지는지 확인";
    }

    @Override
    public String claim() {
        return "한 현장 실행기의 장애는 다른 실행기와 중앙 관제 서버를 중단시키지 않습니다.";
    }

    @Override
    public String criterion() {
        return "Agent A 강제 종료 뒤 Agent B Task 성공, 중앙 API 200";
    }

    @Override
    public Map<String, Object> measure() throws Exception {
        var firstIdentity = environment.registerAgent("validity-a");
        var secondIdentity = environment.registerAgent("validity-b");
        long firstTaskId = environment.createTask(firstIdentity.id(), null, backupParameters);
        long secondTaskId = environment.createTask(secondIdentity.id(), null, backupParameters);
        GenericContainer<?> firstAgent = environment.startAgent(firstIdentity, 30);
        GenericContainer<?> secondAgent = environment.startAgent(secondIdentity, 1);
        GenericContainer<?> restartedFirstAgent = null;

        try {
            waitUntilFirstAgentStarts(firstTaskId);
            kill(firstAgent);
            waitUntilSecondAgentFinishes(secondTaskId);
            waitUntilFirstTaskIsRequeued(firstTaskId);
            restartedFirstAgent = environment.startAgent(firstIdentity, 1);
            waitUntilFirstTaskFinishes(firstTaskId);

            return CheckValues.inOrder(
                    "종료한 Agent의 Task", environment.taskStatus(firstTaskId),
                    "다른 Agent의 Task", environment.taskStatus(secondTaskId),
                    "중앙 API 상태", environment.get("/actuator/health").status());
        } finally {
            firstAgent.stop();
            if (restartedFirstAgent != null) restartedFirstAgent.stop();
            secondAgent.stop();
        }
    }

    private void waitUntilFirstAgentStarts(long taskId) throws Exception {
        if (!environment.await(Duration.ofSeconds(10),
                current -> "RUNNING".equals(current.taskStatus(taskId)))) {
            throw new IllegalStateException("종료할 Agent의 Task가 실행을 시작하지 않았습니다.");
        }
    }

    private void kill(GenericContainer<?> agent) {
        agent.getDockerClient().killContainerCmd(agent.getContainerId())
                .withSignal("SIGKILL").exec();
    }

    private void waitUntilSecondAgentFinishes(long taskId) throws Exception {
        if (!environment.await(Duration.ofSeconds(10),
                current -> "SUCCEEDED".equals(current.taskStatus(taskId)))) {
            throw new IllegalStateException("다른 Agent의 Task가 완료되지 않았습니다.");
        }
    }

    private void waitUntilFirstTaskIsRequeued(long taskId) throws Exception {
        if (!environment.await(Duration.ofSeconds(12),
                current -> "QUEUED".equals(current.taskStatus(taskId)))) {
            throw new IllegalStateException("종료된 Agent의 Task Lease가 회수되지 않았습니다.");
        }
    }

    private void waitUntilFirstTaskFinishes(long taskId) throws Exception {
        if (!environment.await(Duration.ofSeconds(10),
                current -> "SUCCEEDED".equals(current.taskStatus(taskId)))) {
            throw new IllegalStateException("재시작한 Agent가 회수된 Task를 완료하지 못했습니다.");
        }
    }

    @Override
    public boolean supports(Map<String, Object> measurements) {
        return "SUCCEEDED".equals(measurements.get("종료한 Agent의 Task"))
                && "SUCCEEDED".equals(measurements.get("다른 Agent의 Task"))
                && ((Number) measurements.get("중앙 API 상태")).intValue() == 200;
    }

    @Override
    public String limitation() {
        return "같은 Agent 자격으로 재시작한 경우의 복구입니다. 다른 Agent로 재배정하는 기능은 범위가 아닙니다.";
    }

}
