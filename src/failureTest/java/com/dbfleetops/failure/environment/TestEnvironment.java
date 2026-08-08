package com.dbfleetops.failure.environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class TestEnvironment implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String MYSQL_IMAGE = "mysql:8.0.39";
    private static final String JAVA_IMAGE = "eclipse-temurin:21-jre";
    private static final String TOXIPROXY_IMAGE = "ghcr.io/shopify/toxiproxy:2.12.0";

    private final Network network = Network.newNetwork();
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final MySQLContainer metadata = new MySQLContainer(MYSQL_IMAGE)
            .withDatabaseName("db_fleetops").withUsername("dbfleet").withPassword("dbfleetpw")
            .withNetwork(network).withNetworkAliases("metadata-mysql");
    private GenericContainer<?> controlPlane;
    private ToxiproxyContainer toxiproxy;

    public void start() throws Exception {
        metadata.start();
        startControlPlane();
    }

    public void restartControlPlane() {
        if (controlPlane != null)
            controlPlane.stop();
        startControlPlane();
    }

    private void startControlPlane() {
        Path jar = findBootJar();
        controlPlane = createControlPlane(jar);
        controlPlane.start();
    }

    private Path findBootJar() {
        try {
            return Files.list(Path.of("build/libs"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().contains("plain"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new IllegalStateException("실행할 bootJar를 찾을 수 없습니다."));
        } catch (Exception exception) {
            throw new IllegalStateException("실행할 bootJar를 찾을 수 없습니다.", exception);
        }
    }

    private GenericContainer<?> createControlPlane(Path jar) {
        return new GenericContainer<>(JAVA_IMAGE).withNetwork(network)
                .withNetworkAliases("control-plane").withExposedPorts(8080)
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/app/app.jar")
                .withEnv("SPRING_PROFILES_ACTIVE", "docker")
                .withEnv("DB_FLEETOPS_DATASOURCE_URL",
                        "jdbc:mysql://metadata-mysql:3306/db_fleetops?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC")
                .withEnv("DB_FLEETOPS_DATASOURCE_USERNAME", "dbfleet")
                .withEnv("DB_FLEETOPS_DATASOURCE_PASSWORD", "dbfleetpw")
                .withEnv("DB_FLEETOPS_CREDENTIAL_ENCRYPTION_KEY",
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .withEnv("DB_FLEETOPS_JPA_DDL_AUTO", "update")
                .withEnv("DB_FLEETOPS_TASK_LEASE_DURATION", "6s")
                .withEnv("DB_FLEETOPS_TASK_LEASE_RENEWAL_INTERVAL", "2s")
                .withEnv("DB_FLEETOPS_TASK_LEASE_EXPIRATION_CHECK_INTERVAL", "1s")
                .withCommand("java", "-jar", "/app/app.jar")
                .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)));
    }

    public AgentIdentity registerAgent(String suffix) throws Exception {
        JsonNode response = post("/internal/v1/agents/register",
                Map.of("agentName", "failure-agent-" + suffix, "hostname", "failure-host-" + suffix,
                        "ipAddress", "172.18.0.10", "osName", "linux", "architecture", "amd64",
                        "agentVersion", "agent-failure"),
                Duration.ofSeconds(5)).body();
        return new AgentIdentity(response.path("agentId").asLong(),
                response.path("agentToken").asText());
    }

    public long createTask(long agentId, Long jobId, String parametersJson) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", agentId);
        payload.put("operationJobId", jobId);
        payload.put("taskType", "MYSQL_LOGICAL_BACKUP");
        payload.put("parametersJson", parametersJson);
        return post("/internal/v1/agents/tasks", payload, Duration.ofSeconds(5)).body()
                .path("taskId").asLong();
    }

    public HttpResult get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return send(request);
    }

    public HttpResult post(String path, Object body, Duration timeout) throws Exception {
        return postTo(baseUrl(), path, body, timeout);
    }

    public HttpResult postTo(String base, String path, Object body, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
        return send(request);
    }

    private HttpResult send(HttpRequest request) throws Exception {
        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode body = response.body().isBlank() ? JSON.createObjectNode()
                : JSON.readTree(response.body());
        return new HttpResult(response.statusCode(), body, response.body());
    }

    public GenericContainer<?> startAgent(AgentIdentity identity, int dumpSeconds) {
        Path root = Path.of("").toAbsolutePath();
        ImageFromDockerfile image = new ImageFromDockerfile("db-fleetops-agent-failure-test", false)
                .withFileFromPath(".", root)
                .withDockerfilePath("src/failureTest/resources/agent/Dockerfile");
        GenericContainer<?> agent = new GenericContainer<>(image).withNetwork(network)
                .withEnv("CONTROL_PLANE_URL", "http://control-plane:8080")
                .withEnv("AGENT_ID", Long.toString(identity.id()))
                .withEnv("AGENT_TOKEN", identity.token())
                .withEnv("AGENT_STATE_FILE", "/tmp/agent-state.json")
                .withCopyToContainer(Transferable.of("{\"agentId\":" + identity.id()
                        + ",\"agentToken\":\"" + identity.token() + "\"}", 0600),
                        "/tmp/agent-state.json")
                .withEnv("AGENT_NAME", "agent-failure-agent")
                .withEnv("HEARTBEAT_INTERVAL_SECONDS", "2").withEnv("POLL_INTERVAL_SECONDS", "1")
                .withEnv("LEASE_RENEWAL_INTERVAL_SECONDS", "2")
                .withEnv("TASK_LEASE_DURATION_SECONDS", "6")
                .withEnv("BACKUP_DIRECTORY", "/tmp/evidence-backups")
                .withEnv("FAKE_MYSQLDUMP_SLEEP_SECONDS", Integer.toString(dumpSeconds))
                .waitingFor(Wait.forLogMessage(".*agent_runtime_started.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(30)));
        agent.start();
        return agent;
    }

    public ToxiproxyContainer.ContainerProxy startResponseLossProxy() {
        if (toxiproxy == null) {
            toxiproxy = new ToxiproxyContainer(TOXIPROXY_IMAGE).withNetwork(network);
            toxiproxy.start();
        }
        return toxiproxy.getProxy(controlPlane, 8080);
    }

    public String proxyBaseUrl(ToxiproxyContainer.ContainerProxy proxy) {
        return "http://" + toxiproxy.getHost() + ":" + proxy.getProxyPort();
    }

    public String baseUrl() {
        return "http://" + controlPlane.getHost() + ":" + controlPlane.getMappedPort(8080);
    }

    public String nextTaskPath(AgentIdentity identity) {
        return "/internal/v1/agents/" + identity.id() + "/tasks/next?agentToken="
                + URLEncoder.encode(identity.token(), StandardCharsets.UTF_8);
    }

    public HttpResult claimNextTask(AgentIdentity identity) throws Exception {
        return post(nextTaskPath(identity), Map.of(), Duration.ofSeconds(5));
    }

    public String taskStatus(long taskId) throws Exception {
        return queryString("select status from operation_task where id=" + taskId);
    }

    public Instant heartbeatAt(long agentId) throws Exception {
        try (Connection connection = connection();
                var statement = connection.createStatement();
                ResultSet rs = statement
                        .executeQuery("select last_heartbeat_at from agent where id=" + agentId)) {
            if (!rs.next() || rs.getTimestamp(1) == null) {
                return null;
            }
            LocalDateTime value = rs.getTimestamp(1).toLocalDateTime();
            return value.toInstant(ZoneOffset.UTC);
        }
    }

    public long countTasks(long jobId, String type) throws Exception {
        return queryLong("select count(*) from operation_task where operation_job_id=" + jobId
                + " and task_type='" + type + "'");
    }

    public long insertRunningJob() throws Exception {
        try (Connection connection = connection();
                var statement = connection.prepareStatement(
                        "insert into operation_job (job_type,target_database_id,status,requested_by,"
                                + "idempotency_key,priority,retry_count,max_retry_count,available_at,"
                                + "started_at,version,created_at,updated_at) values "
                                + "('BACKUP',1,'RUNNING','failure-test',?,0,0,3,now(),now(),0,now(),now())",
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "failure-" + System.nanoTime());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public boolean await(Duration timeout, CheckedCondition condition) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            if (condition.test(this)) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(250);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(metadata.getJdbcUrl(), metadata.getUsername(),
                metadata.getPassword());
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = connection();
                var statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private long queryLong(String sql) throws Exception {
        try (Connection connection = connection();
                var statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Override
    public void close() {
        if (toxiproxy != null)
            toxiproxy.stop();
        if (controlPlane != null)
            controlPlane.stop();
        metadata.stop();
        network.close();
    }

    public record AgentIdentity(long id, String token) {
    }

    public record HttpResult(int status, JsonNode body, String rawBody) {
    }

    @FunctionalInterface
    public interface CheckedCondition {
        boolean test(TestEnvironment environment) throws Exception;
    }
}
