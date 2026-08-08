package com.dbfleetops.failure.validity;

import com.dbfleetops.failure.environment.TestEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.DockerClientFactory;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 중앙 관제 서버와 현장 실행기를 분리하면서 기대한 장점을 차례대로 검증합니다.
 * 각 테스트는 자세한 절차를 직접 구현하지 않고, 이름에 맞는 실험을 실행자에게 전달합니다.
 */
@Tag("architecture-check")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ArchitectureTest {
    private static final String BACKUP_PARAMETERS = """
            {"databaseName":"validity","host":"managed-mysql","port":3306,
             "username":"validity_user","password":"validity_password",
             "backupType":"LOGICAL","compression":false,"verifyAfterBackup":false}
            """;
    private static TestEnvironment environment;
    private static CheckRunner runner;

    @BeforeAll
    static void prepareArchitectureTestEnvironment() throws Exception {
        requireDocker();

        System.out.println("[환경] 구조 타당성 실험용 MySQL과 중앙 관제 서버를 시작합니다.");
        environment = new TestEnvironment();
        environment.start();
        runner = new CheckRunner();
    }

    @AfterAll
    static void stopEnvironment() {
        if (environment != null) environment.close();
    }

    @Test
    @Order(1)
    @DisplayName("긴 현장 작업 중에도 중앙 관제 서버는 계속 응답합니다")
    void verifyLongTaskAndControlPlaneAreIsolated() throws Exception {
        runner.verify(new LongTaskCheck(
                environment, BACKUP_PARAMETERS));
    }

    @Test
    @Order(2)
    @DisplayName("현장 실행기는 외부 수신 Port 없이 작업을 완료합니다")
    void verifyPullCommunicationBoundary() throws Exception {
        runner.verify(new PullAgentCheck(
                environment, BACKUP_PARAMETERS));
    }

    @Test
    @Order(3)
    @DisplayName("한 현장 실행기의 장애는 다른 실행기로 번지지 않습니다")
    void verifyAgentFailureIsIsolated() throws Exception {
        runner.verify(new AgentRecoveryCheck(
                environment, BACKUP_PARAMETERS));
    }

    @Test
    @Order(4)
    @DisplayName("중앙 관제 서버를 재시작해도 작업 장부는 남습니다")
    void verifyCentralStateSurvivesRestart() throws Exception {
        runner.verify(new StatePersistenceCheck(
                environment, BACKUP_PARAMETERS));
    }

    @Test
    @Order(5)
    @DisplayName("Go Agent는 프로젝트의 배포와 실행 기준을 만족합니다")
    void verifyGoAgentFitsOperationalRequirements() throws Exception {
        runner.verify(new AgentRuntimeCheck(environment));
    }

    private static void requireDocker() {
        boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        boolean dockerRequired = Boolean.parseBoolean(
                System.getProperty("failureTest.requireDocker", "false"));

        if (!dockerAvailable && dockerRequired) {
            throw new IllegalStateException("Docker 필수 실행이지만 Docker를 사용할 수 없습니다.");
        }
        assumeTrue(dockerAvailable, "Docker를 사용할 수 없어 구조 타당성 실험을 건너뜁니다.");
    }
}
