package com.dbfleetops.failure.validity;

import com.dbfleetops.failure.environment.TestEnvironment;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.Map;

/** 현장 실행기가 외부 수신 Port 없이도 전체 작업 흐름을 수행하는지 확인합니다. */
final class PullAgentCheck implements ArchitectureCheck {
    private final TestEnvironment environment;
    private final String backupParameters;

    PullAgentCheck(TestEnvironment environment, String backupParameters) {
        this.environment = environment;
        this.backupParameters = backupParameters;
    }

    @Override
    public String title() {
        return "Pull 통신과 현장 실행기 수신 Port 제거";
    }

    @Override
    public String claim() {
        return "현장 실행기는 수신 Port 없이 자신이 시작한 연결만으로 작업할 수 있습니다.";
    }

    @Override
    public String criterion() {
        return "공개 Host Port 0개이며 등록·생존 연락·작업 완료 성공";
    }

    @Override
    public Map<String, Object> measure() throws Exception {
        var agentIdentity = environment.registerAgent("validity-pull");
        GenericContainer<?> agent = environment.startAgent(agentIdentity, 0);

        try {
            long taskId = environment.createTask(agentIdentity.id(), null, backupParameters);
            if (!environment.await(Duration.ofSeconds(10),
                    current -> "SUCCEEDED".equals(current.taskStatus(taskId)))) {
                throw new IllegalStateException("Agent가 Task를 완료하지 못했습니다.");
            }

            var bindings = agent.getContainerInfo().getHostConfig().getPortBindings().getBindings();
            int publishedPorts = bindings == null ? 0 : bindings.size();
            return CheckValues.inOrder(
                    "Agent가 공개한 Host Port 수", publishedPorts,
                    "등록", "성공",
                    "생존 연락", environment.heartbeatAt(agentIdentity.id()) == null ? "실패" : "성공",
                    "작업 완료", environment.taskStatus(taskId));
        } finally {
            agent.stop();
        }
    }

    @Override
    public boolean supports(Map<String, Object> measurements) {
        return ((Number) measurements.get("Agent가 공개한 Host Port 수")).intValue() == 0
                && "성공".equals(measurements.get("등록"))
                && "성공".equals(measurements.get("생존 연락"))
                && "SUCCEEDED".equals(measurements.get("작업 완료"));
    }

    @Override
    public String limitation() {
        return "Docker Port 공개 여부를 확인한 결과입니다. 실제 회사 방화벽 규칙은 별도 환경에서 확인해야 합니다.";
    }

}
