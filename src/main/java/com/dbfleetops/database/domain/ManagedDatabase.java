package com.dbfleetops.database.domain;

import com.dbfleetops.database.dto.RegisterManagedDatabaseRequest;
import lombok.Getter;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.isTrue;
import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;

@Getter
@Entity
public class ManagedDatabase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String host;

    private int port;

    private String databaseName;

    @Enumerated(EnumType.STRING)
    private DatabaseEngine engine;

    @Enumerated(EnumType.STRING)
    private DatabaseStatus status;

    private String environment;

    private String serviceName;

    private String owner;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected ManagedDatabase() {}

    private ManagedDatabase(RegisterManagedDatabaseRequest request) {
        validateConnection(request.host(), request.port(), request.databaseName(), request.engine());
        validateMetadata(request.name(), request.environment());

        this.name = request.name();
        this.host = request.host();
        this.port = request.port();
        this.databaseName = request.databaseName();
        this.engine = request.engine();
        this.environment = request.environment();
        this.serviceName = request.serviceName();
        this.owner = request.owner();
        this.description = request.description();
        this.status = DatabaseStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static ManagedDatabase register(RegisterManagedDatabaseRequest request) {
        notNull(request, "관리 데이터베이스 등록 요청은 필수입니다.");
        return new ManagedDatabase(request);
    }

    public void changeConnection(String host, int port, String databaseName,
            DatabaseEngine engine) {
        validateConnection(host, port, databaseName, engine);

        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.engine = engine;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeMetadata(String name, String environment, String serviceName, String owner,
            String description) {
        validateMetadata(name, environment);

        this.name = name;
        this.environment = environment;
        this.serviceName = serviceName;
        this.owner = owner;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        state(status == DatabaseStatus.ACTIVE,
                "이미 비활성화된 데이터베이스입니다.");

        this.status = DatabaseStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void requireActive() {
        state(status == DatabaseStatus.ACTIVE,
                "비활성화된 데이터베이스는 운영 작업을 수행할 수 없습니다.");
    }

    private static void validateConnection(String host, int port, String databaseName,
            DatabaseEngine engine) {
        hasText(host, "데이터베이스 호스트는 필수입니다.");
        isTrue(port >= 1 && port <= 65535,
                "데이터베이스 포트는 1에서 65535 사이여야 합니다.");
        hasText(databaseName, "논리 데이터베이스 이름은 필수입니다.");
        notNull(engine, "데이터베이스 엔진은 필수입니다.");
    }

    private static void validateMetadata(String name, String environment) {
        hasText(name, "데이터베이스 이름은 필수입니다.");
        hasText(environment, "운영 환경은 필수입니다.");
    }

}
