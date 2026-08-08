package com.dbfleetops.failure.validity;

import com.dbfleetops.failure.environment.TestEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Go Agent가 프로젝트에서 정한 배포와 실행 기준을 만족하는지 측정합니다. */
final class AgentRuntimeCheck implements ArchitectureCheck {
    private static final long MAXIMUM_BINARY_BYTES = 25L * 1024 * 1024;
    private static final long MAXIMUM_IDLE_MEMORY_KIB = 40L * 1024;
    private static final long MAXIMUM_STARTUP_MILLIS = 15_000;

    private final TestEnvironment environment;

    AgentRuntimeCheck(TestEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public String title() {
        return "Go를 현장 실행기에 사용한 이유를 수치로 확인";
    }

    @Override
    public String claim() {
        return "Go Agent는 작은 단일 실행 파일로 빠르게 시작하며 두 CPU 종류로 Build할 수 있습니다.";
    }

    @Override
    public String criterion() {
        return "실행 파일 25MiB 이하, 유휴 Memory 40MiB 이하, 시작 15초 미만, amd64·arm64 Build 성공";
    }

    @Override
    public Map<String, Object> measure() throws Exception {
        var agentIdentity = environment.registerAgent("validity-go");
        Instant startingAt = Instant.now();
        GenericContainer<?> agent = environment.startAgent(agentIdentity, 0);
        long startupMillis = Duration.between(startingAt, Instant.now()).toMillis();
        GenericContainer<?> buildCheck = null;

        try {
            long binaryBytes = readBinarySize(agent);
            long idleMemoryKib = readIdleMemory(agent);
            long imageBytes = readRuntimeImageSize(agent);
            buildCheck = buildForSupportedCpuArchitectures();
            String buildLog = buildCheck.getLogs();

            return CheckValues.inOrder(
                    "Go 실행 파일 크기(MiB)", CheckValues.mebibytes(binaryBytes),
                    "유휴 Process Memory(MiB)",
                    CheckValues.mebibytes(idleMemoryKib * 1024),
                    "Agent 시작 시간(ms)", startupMillis,
                    "전체 Runtime Image 크기(MiB)", CheckValues.mebibytes(imageBytes),
                    "linux/amd64 Build", buildLog.contains("AMD64="),
                    "linux/arm64 Build", buildLog.contains("ARM64="));
        } finally {
            if (buildCheck != null) buildCheck.stop();
            agent.stop();
        }
    }

    private long readBinarySize(GenericContainer<?> agent) throws Exception {
        return Long.parseLong(agent.execInContainer("sh", "-c",
                "stat -c %s /usr/local/bin/db-fleet-agent").getStdout().trim());
    }

    private long readIdleMemory(GenericContainer<?> agent) throws Exception {
        return Long.parseLong(agent.execInContainer("sh", "-c",
                "awk '/VmRSS/{print $2}' /proc/1/status").getStdout().trim());
    }

    private long readRuntimeImageSize(GenericContainer<?> agent) {
        return agent.getDockerClient().inspectImageCmd(agent.getDockerImageName()).exec().getSize();
    }

    private GenericContainer<?> buildForSupportedCpuArchitectures() {
        GenericContainer<?> buildCheck = new GenericContainer<>("golang:1.24-alpine")
                .withCopyFileToContainer(MountableFile.forHostPath(
                        Path.of("agent-go").toAbsolutePath()), "/src")
                .withCommand("sh", "-c", buildCommand())
                .waitingFor(Wait.forLogMessage(".*BUILD_OK.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        buildCheck.start();
        return buildCheck;
    }

    private String buildCommand() {
        return "cd /src && "
                + "GOOS=linux GOARCH=amd64 go build -o /tmp/agent-amd64 ./cmd/db-fleet-agent && "
                + "GOOS=linux GOARCH=arm64 go build -o /tmp/agent-arm64 ./cmd/db-fleet-agent && "
                + "echo BUILD_OK AMD64=$(stat -c %s /tmp/agent-amd64) "
                + "ARM64=$(stat -c %s /tmp/agent-arm64) && sleep 60";
    }

    @Override
    public boolean supports(Map<String, Object> measurements) {
        double binaryMiB = ((Number) measurements.get("Go 실행 파일 크기(MiB)")).doubleValue();
        double idleMemoryMiB = ((Number) measurements.get("유휴 Process Memory(MiB)")).doubleValue();
        long startupMillis = ((Number) measurements.get("Agent 시작 시간(ms)")).longValue();
        return binaryMiB * 1024 * 1024 <= MAXIMUM_BINARY_BYTES
                && idleMemoryMiB * 1024 <= MAXIMUM_IDLE_MEMORY_KIB
                && startupMillis < MAXIMUM_STARTUP_MILLIS
                && (boolean) measurements.get("linux/amd64 Build")
                && (boolean) measurements.get("linux/arm64 Build");
    }

    @Override
    public String limitation() {
        return "동일 기능의 Java Agent가 없어 언어 간 우열은 주장하지 않습니다. MySQL 도구를 포함한 Runtime Image 크기도 별도로 공개합니다.";
    }

}
