package com.dbfleetops.database.domain;

import com.dbfleetops.database.dto.RegisterManagedDatabaseRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ManagedDatabaseTest {

    @Test
    void createManagedDatabase() {
        ManagedDatabase database = newDatabase();

        assertThat(database.getName()).isEqualTo("주문 데이터베이스");
        assertThat(database.getHost()).isEqualTo("order-db.internal");
        assertThat(database.getPort()).isEqualTo(3306);
        assertThat(database.getDatabaseName()).isEqualTo("orders");
        assertThat(database.getEngine()).isEqualTo(DatabaseEngine.MYSQL);
        assertThat(database.getEnvironment()).isEqualTo("PRODUCTION");
        assertThat(database.getStatus()).isEqualTo(DatabaseStatus.ACTIVE);
        assertThat(database.getCreatedAt()).isNotNull();
        assertThat(database.getUpdatedAt()).isNotNull();
    }

    @Test
    void requiredValuesAreValidatedWhenCreatingDatabase() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ManagedDatabase.register(null))
                .withMessage("관리 데이터베이스 등록 요청은 필수입니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ManagedDatabase.register(registration(" ",
                        "order-db.internal", 3306, "orders", DatabaseEngine.MYSQL, "PRODUCTION")))
                .withMessage("데이터베이스 이름은 필수입니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ManagedDatabase.register(registration("주문 데이터베이스", null,
                        3306, "orders", DatabaseEngine.MYSQL, "PRODUCTION")))
                .withMessage("데이터베이스 호스트는 필수입니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ManagedDatabase.register(registration("주문 데이터베이스",
                        "order-db.internal", 0, "orders", DatabaseEngine.MYSQL, "PRODUCTION")))
                .withMessage("데이터베이스 포트는 1에서 65535 사이여야 합니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ManagedDatabase.register(registration("주문 데이터베이스",
                        "order-db.internal", 3306, "", DatabaseEngine.MYSQL, "PRODUCTION")))
                .withMessage("논리 데이터베이스 이름은 필수입니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ManagedDatabase.register(registration("주문 데이터베이스",
                        "order-db.internal", 3306, "orders", null, "PRODUCTION")))
                .withMessage("데이터베이스 엔진은 필수입니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ManagedDatabase.register(registration("주문 데이터베이스",
                        "order-db.internal", 3306, "orders", DatabaseEngine.MYSQL, " ")))
                .withMessage("운영 환경은 필수입니다.");
    }

    @Test
    void changeConnection() {
        ManagedDatabase database = newDatabase();

        database.changeConnection("new-order-db.internal", 5432, "new_orders",
                DatabaseEngine.POSTGRESQL);

        assertThat(database.getHost()).isEqualTo("new-order-db.internal");
        assertThat(database.getPort()).isEqualTo(5432);
        assertThat(database.getDatabaseName()).isEqualTo("new_orders");
        assertThat(database.getEngine()).isEqualTo(DatabaseEngine.POSTGRESQL);
    }

    @Test
    void invalidConnectionDoesNotChangeExistingConnection() {
        ManagedDatabase database = newDatabase();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> database.changeConnection("new-order-db.internal", 70000,
                        "new_orders", DatabaseEngine.POSTGRESQL))
                .withMessage("데이터베이스 포트는 1에서 65535 사이여야 합니다.");

        assertThat(database.getHost()).isEqualTo("order-db.internal");
        assertThat(database.getPort()).isEqualTo(3306);
        assertThat(database.getDatabaseName()).isEqualTo("orders");
        assertThat(database.getEngine()).isEqualTo(DatabaseEngine.MYSQL);
    }

    @Test
    void changeMetadata() {
        ManagedDatabase database = newDatabase();

        database.changeMetadata("새 주문 데이터베이스", "STAGING", "new-order-service",
                "database-team", "전환 대상");

        assertThat(database.getName()).isEqualTo("새 주문 데이터베이스");
        assertThat(database.getEnvironment()).isEqualTo("STAGING");
        assertThat(database.getServiceName()).isEqualTo("new-order-service");
        assertThat(database.getOwner()).isEqualTo("database-team");
        assertThat(database.getDescription()).isEqualTo("전환 대상");
    }

    @Test
    void invalidMetadataDoesNotChangeExistingMetadata() {
        ManagedDatabase database = newDatabase();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> database.changeMetadata("새 주문 데이터베이스", " ",
                        "new-order-service", "database-team", "전환 대상"))
                .withMessage("운영 환경은 필수입니다.");

        assertThat(database.getName()).isEqualTo("주문 데이터베이스");
        assertThat(database.getEnvironment()).isEqualTo("PRODUCTION");
        assertThat(database.getServiceName()).isEqualTo("order-service");
        assertThat(database.getOwner()).isEqualTo("platform-team");
        assertThat(database.getDescription()).isEqualTo("주문 서비스 데이터베이스");
    }

    @Test
    void activeDatabaseAllowsOperation() {
        assertThatCode(() -> newDatabase().requireActive()).doesNotThrowAnyException();
    }

    @Test
    void inactiveDatabaseRejectsOperation() {
        ManagedDatabase database = newDatabase();
        database.deactivate();

        assertThatIllegalStateException()
                .isThrownBy(database::requireActive)
                .withMessage("비활성화된 데이터베이스는 운영 작업을 수행할 수 없습니다.");
    }

    @Test
    void inactiveDatabaseCannotBeDeactivatedAgain() {
        ManagedDatabase database = newDatabase();
        database.deactivate();

        assertThatIllegalStateException()
                .isThrownBy(database::deactivate)
                .withMessage("이미 비활성화된 데이터베이스입니다.");

        assertThat(database.getStatus()).isEqualTo(DatabaseStatus.INACTIVE);
    }

    private ManagedDatabase newDatabase() {
        return ManagedDatabase.register(new RegisterManagedDatabaseRequest("주문 데이터베이스",
                "order-db.internal", 3306, "orders", DatabaseEngine.MYSQL, "PRODUCTION",
                "order-service", "platform-team", "주문 서비스 데이터베이스"));
    }

    private RegisterManagedDatabaseRequest registration(String name, String host, int port,
            String databaseName, DatabaseEngine engine, String environment) {
        return new RegisterManagedDatabaseRequest(name, host, port, databaseName, engine,
                environment, null, null, null);
    }
}
